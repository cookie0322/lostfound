package edu.campus.lostfound.claim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.lostfound.audit.AuditRecorder;
import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.data.entity.ClaimHold;
import edu.campus.lostfound.data.entity.ClaimTransition;
import edu.campus.lostfound.data.mapper.ClaimHoldMapper;
import edu.campus.lostfound.data.mapper.ClaimTransitionMapper;
import edu.campus.lostfound.notice.NoticeService;
import edu.campus.lostfound.web.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClaimStateMachine {
    private final ClaimTransitionMapper transitionMapper;
    private final ClaimHoldMapper claimHoldMapper;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;

    /** KV-03：关系结束后凭证再保留多少天（QR-04 的口径是 30 天） */
    @Value("${lf.visibility.voucher-hidden-after-days:30}")
    private int voucherHiddenAfterDays;

    // 内存中的转移规则字典，Key 格式："0_ACCEPT"
    private volatile Map<String, ClaimTransition> rules = Map.of();

    public ClaimStateMachine(ClaimTransitionMapper transitionMapper,
                             ClaimHoldMapper claimHoldMapper,
                             AuditRecorder auditRecorder,
                             ObjectMapper objectMapper,
                             NoticeService noticeService) {
        this.transitionMapper = transitionMapper;
        this.claimHoldMapper = claimHoldMapper;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.noticeService = noticeService;
    }
    @PostConstruct
    @Scheduled(fixedRate = 60000) // 60s
    public void loadRules() {
        try {
            List<ClaimTransition> list = transitionMapper.selectList(null);
            Map<String, ClaimTransition> freshRules = new HashMap<>();
            for (ClaimTransition t : list) {
                freshRules.put(t.getFromState() + "_" + t.getEventCode(), t);
            }
            // 应修 8 修改点：整体替换引用，消除先 clear 后 put 造成的空窗期！
            rules = Map.copyOf(freshRules);
            System.out.println("状态转移规则已刷新，共加载 " + rules.size() + " 条");
        } catch (Exception e) {
            System.err.println("加载状态转移规则失败，沿用旧规则: " + e.getMessage());
        }
    }

    /**
     * 状态机唯一入口
     */
    public void transition(Claim claim, String event, Long operatorId, String role, String reason) {
        String key = claim.getState() + "_" + event;
        ClaimTransition rule = rules.get(key);

        // 规则不存在，说明是非法跃迁
        if (rule == null) {
            throw BizException.conflict("当前状态不允许此操作（非法跃迁）");
        }

        int fromState = claim.getState();
        int toState = rule.getToState();

        // 应用规则
        claim.setState(toState);

        Integer endReason = switch (event) {
            case "REJECT" -> 1;
            case "TIMEOUT" -> 2;
            case "WITHDRAW" -> 3;
            // 事件名必须与 claim_transition.event_code 一致，否则走到 default 变成 null
            case "ARBITRATE_REJECT" -> 4;
            default -> null;
        };
        claim.setEndReason(endReason);

        // 针对特定事件的业务副作用（比如延长超时时间）
        if ("ACCEPT".equals(event)) {
            claim.setExpireAt(LocalDateTime.now().plusHours(72));
        }

        // 已完成/已结束作为终态
        if (toState == ClaimState.ENDED.code() || toState == ClaimState.COMPLETED.code()) {
            // 释放占用：只存活跃占用，释放即删（架构 3.1 决策 12）
            claimHoldMapper.delete(new LambdaQueryWrapper<ClaimHold>()
                    .eq(ClaimHold::getClaimId, claim.getClaimId()));
            // KV-03：关系结束后凭证再保留 N 天，之后即使有 URL 也不可查
            if (claim.getVoucherUrl() != null) {
                claim.setVoucherVisibleUntil(LocalDateTime.now().plusDays(voucherHiddenAfterDays));
            }
        }

        String detailJson = null;
        try {
            detailJson = objectMapper.writeValueAsString(Map.of("reason", reason != null ? reason : ""));
        } catch (Exception e) {
            detailJson = "{}";
        }

        auditRecorder.record("CLAIM", claim.getClaimId(), event,
                String.valueOf(fromState), String.valueOf(toState),
                operatorId, role, detailJson);

        Long applicantId = claim.getApplicantId();
        Long respondentId = claim.getRespondentId();
        Long claimId = claim.getClaimId();

        switch (event) {
            case "ACCEPT" -> noticeService.send(applicantId, claimId, "VERDICT",
                    "认领申请已通过", "对方已同意您的认领申请，请尽快完成线下交接并确认。");
            case "REJECT" -> noticeService.send(applicantId, claimId, "VERDICT",
                    "认领申请被拒绝", "对方拒绝了您的申请。理由：" + reason);
            case "TO_ARBITRATION" -> {
                noticeService.send(applicantId, claimId, "VERDICT", "认领进入仲裁", "对方将认领转入仲裁，请等待管理员裁决。");
                noticeService.send(respondentId, claimId, "VERDICT", "认领进入仲裁", "认领已转入仲裁，请等待管理员裁决。");
            }
            case "CONFIRM" -> {
                // 单方确认，通知另一方
                Long targetUser = operatorId != null && operatorId.equals(applicantId) ? respondentId : applicantId;
                noticeService.send(targetUser, claimId, "CONFIRM", "待您确认交接", "对方已确认完成交接，请及时确认。");
            }
            case "BOTH_CONFIRMED", "ARBITRATE_RETURN" -> {
                noticeService.send(applicantId, claimId, "COMPLETED", "认领已完成", "双方已确认或管理员判定归还，本次认领流程已完成。");
                noticeService.send(respondentId, claimId, "COMPLETED", "认领已完成", "双方已确认或管理员判定归还，本次认领流程已完成。");
            }
            case "TIMEOUT", "WITHDRAW", "ARBITRATE_REJECT" -> {
                String endMsg = "流程已结束。";
                if ("TIMEOUT".equals(event)) endMsg = "超时未处理，流程已自动结束。";
                if ("WITHDRAW".equals(event)) endMsg = "申请人已撤销申请。";
                if ("ARBITRATE_REJECT".equals(event)) endMsg = "管理员判定驳回，流程已结束。";
                noticeService.send(applicantId, claimId, "CLOSED", "认领已结束", endMsg);
                noticeService.send(respondentId, claimId, "CLOSED", "认领已结束", endMsg);
            }
        }
    }
}
