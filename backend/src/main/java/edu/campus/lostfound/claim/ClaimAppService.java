package edu.campus.lostfound.claim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.lostfound.auth.AuthStatusQuery;
import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.data.entity.ClaimHold;
import edu.campus.lostfound.data.entity.Item;
import edu.campus.lostfound.data.mapper.ClaimHoldMapper;
import edu.campus.lostfound.data.mapper.ClaimMapper;
import edu.campus.lostfound.data.mapper.ItemMapper;
import edu.campus.lostfound.data.storage.VoucherStorage;
import edu.campus.lostfound.notice.NoticeService;
import edu.campus.lostfound.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ClaimAppService {

    private final ClaimMapper claimMapper;
    private final ClaimHoldMapper claimHoldMapper;
    private final ClaimStateMachine claimStateMachine;
    private final AuthStatusQuery authStatusQuery;
    private final ItemMapper itemMapper;
    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;
    private final VoucherStorage voucherStorage;

    public ClaimAppService(ClaimMapper claimMapper, ClaimHoldMapper claimHoldMapper, AuthStatusQuery authStatusQuery,
                           ClaimStateMachine claimStateMachine, ItemMapper itemMapper, ObjectMapper objectMapper,
                           NoticeService noticeService, VoucherStorage voucherStorage) {
        this.claimMapper = claimMapper;
        this.claimHoldMapper = claimHoldMapper;
        this.claimStateMachine = claimStateMachine;
        this.authStatusQuery = authStatusQuery;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
        this.noticeService = noticeService;
        this.voucherStorage = voucherStorage;
    }

    @Transactional(rollbackFor = Exception.class) // 开启事务
    public ClaimView apply(Long applicantId, ApplyRequest req,String role) {
        // 2. 申请人未认证 -> 403
        if (!authStatusQuery.isVerified(applicantId)) {
            throw BizException.forbidden("只有已认证用户才能发起认领");
        }

        // 3. 特征少于 3 项 -> 400（空白项不算数）
        List<String> features = req.features() == null ? List.of()
                : req.features().stream().filter(f -> f != null && !f.isBlank()).map(String::trim).toList();
        if (features.size() < 3) {
            throw BizException.badRequest("请至少填写 3 项特征描述");
        }

        // 4. item 不存在 -> 404; item.status != 0 -> 409
        Item item = itemMapper.selectById(req.itemId());
        if (item == null) throw BizException.notFound("招领信息不存在");
        if (item.getStatus() != 0) throw BizException.conflict("该物品已被认领或已结束");

        Long respondentId = item.getPublisherId();

        // 5. 申请人是发布者 -> 400
        if (applicantId.equals(respondentId)) {
            throw BizException.badRequest("不能认领自己发布的招领信息");
        }

        // 组装 Claim
        Claim claim = new Claim();
        claim.setItemId(req.itemId());
        claim.setApplicantId(applicantId);
        claim.setRespondentId(respondentId);
        claim.setState(ClaimState.PENDING.code()); // 0 待处理
        claim.setEvidenceJson(toJson(features));
        claim.setExpireAt(LocalDateTime.now().plusHours(24)); // 超时 +24h
        claim.setCreatedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());

        // 先插入 claim 拿主键
        claimMapper.insert(claim);

        // 组装 ClaimHold
        ClaimHold hold = new ClaimHold();
        hold.setClaimId(claim.getClaimId());
        hold.setItemId(req.itemId());
        hold.setCreatedAt(LocalDateTime.now());

        // 再插入 hold（撞车抛 DuplicateKeyException）
        claimHoldMapper.insert(hold);

        // FR-14 的第一个节点：通知拾得者有人来认领了（APPLIED）
        noticeService.send(respondentId, claim.getClaimId(), "APPLIED",
                "有新的认领申请", "有人对您发布的招领信息发起了认领，请及时核验。");

        return ClaimView.of(claim, applicantId, role);
    }

    public ClaimView getDetail(Long claimId, Long currentUserId, String role) {
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("认领记录不存在");

        // 越权校验：只有申请人、拾得者、管理员可见
        if (!"ADMIN".equals(role)
                && !currentUserId.equals(claim.getApplicantId())
                && !currentUserId.equals(claim.getRespondentId())) {
            throw BizException.forbidden("无权查看此认领详情");
        }
        return ClaimView.of(claim, currentUserId, role);
    }
    public Map<String, List<ClaimView>> getMine(Long userId, String role) {
        // 1. 查“我发起的”（我是申请人 applicant）
        List<Claim> applied = claimMapper.selectList(
                new LambdaQueryWrapper<Claim>()
                        .eq(Claim::getApplicantId, userId)
                        .orderByDesc(Claim::getCreatedAt) // 按时间倒序
        );

        // 2. 查“我收到的”（我是拾得者 respondent）
        List<Claim> received = claimMapper.selectList(
                new LambdaQueryWrapper<Claim>()
                        .eq(Claim::getRespondentId, userId)
                        .orderByDesc(Claim::getCreatedAt)
        );

        List<ClaimView> appliedViews = applied.stream()
                .map(c -> ClaimView.of(c, userId, role))
                .toList();

        List<ClaimView> receivedViews = received.stream()
                .map(c -> ClaimView.of(c, userId, role))
                .toList();

        Map<String, List<ClaimView>> result = new HashMap<>();
        result.put("applied", appliedViews);
        result.put("received", receivedViews);
        return result;
    }

    private static final Map<String, String> ACTION_TO_EVENT = Map.of(
            "ACCEPT", "ACCEPT",
            "REJECT", "REJECT",
            "ARBITRATE", "TO_ARBITRATION"
    );

    // 拾得者核验 (ACCEPT / REJECT / ARBITRATE)
    @Transactional(rollbackFor = Exception.class)
    public void review(Long claimId, Long respondentId, String action, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("必须填写操作理由");
        }
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("记录不存在");

        if (!claim.getRespondentId().equals(respondentId)) {
            throw BizException.forbidden("只有拾得者才能核验此申请");
        }

        if (!authStatusQuery.isVerified(respondentId)) {
            throw BizException.forbidden("只有已认证用户才能核验申请");
        }

        String event = ACTION_TO_EVENT.get(action);
        if (event == null) {
            throw BizException.badRequest("不支持的处置动作: " + action);
        }

        // 只有合法的事件才允许传给状态机
        claimStateMachine.transition(claim, event, respondentId, "USER", reason);
        claimMapper.updateById(claim);
    }

    // 双方均可撤销 (二次确认)
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long claimId, Long userId, String role, boolean confirm) {
        if (!confirm) throw BizException.badRequest("请二次确认撤销操作");

        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("记录不存在");
        if (!userId.equals(claim.getApplicantId()) && !userId.equals(claim.getRespondentId())) {
            throw BizException.forbidden("无权撤销此申请");
        }

        claimStateMachine.transition(claim, "WITHDRAW", userId, role, "用户主动撤销");
        claimMapper.updateById(claim);
    }

    // 双方均可转仲裁
    @Transactional(rollbackFor = Exception.class)
    public void arbitrate(Long claimId, Long userId, String role, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("转仲裁必须附理由");
        }
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("记录不存在");
        if (!userId.equals(claim.getApplicantId()) && !userId.equals(claim.getRespondentId())) {
            throw BizException.forbidden("无权转仲裁");
        }

        // 事件名必须和 claim_transition.event_code 一致（是 TO_ARBITRATION，不是 ARBITRATE）
        claimStateMachine.transition(claim, ACTION_TO_EVENT.get("ARBITRATE"), userId, role, reason);
        claimMapper.updateById(claim);
    }

    // ================= FR-10 超时任务（供定时器调用） =================
    @Transactional(rollbackFor = Exception.class)
    public void processTimeout(Claim claim) {
        int oldState = claim.getState();

        // 1. 调状态机（这里会删除占用记录、写审计）
        claimStateMachine.transition(claim, "TIMEOUT", null, "SYSTEM", "超时自动关闭");

        // 2. 带状态条件更新数据库（如果被别人抢先改了，影响行数为 0）
        int updated = claimMapper.update(claim, new LambdaUpdateWrapper<Claim>()
                .eq(Claim::getClaimId, claim.getClaimId())
                .eq(Claim::getState, oldState) // 核心：乐观锁，防止多节点重复处理
        );

        // 3. 如果更新失败，抛出异常触发整个事务回滚（包括刚才的占用释放和审计）
        if (updated == 0) {
            throw new RuntimeException("并发冲突，任务已被其他节点处理");
        }
    }

    // ================= FR-11 双方确认 =================
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long claimId, Long userId, String role) {
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("记录不存在");
        if (claim.getState() != ClaimState.HANDOVER.code()) {
            throw BizException.conflict("当前状态不允许确认交接");
        }

        boolean isApplicant = userId.equals(claim.getApplicantId());
        boolean isRespondent = userId.equals(claim.getRespondentId());
        if (!isApplicant && !isRespondent) {
            throw BizException.forbidden("无权操作此申请");
        }

        LocalDateTime now = LocalDateTime.now();
        // 先写时间戳，防重复点击
        if (isApplicant) {
            if (claim.getConfirmAAt() != null) throw BizException.conflict("你已经确认过了");
            claim.setConfirmAAt(now);
        } else {
            if (claim.getConfirmRAt() != null) throw BizException.conflict("你已经确认过了");
            claim.setConfirmRAt(now);
        }

        // 判断是否双方都确认了
        boolean bothConfirmed = claim.getConfirmAAt() != null && claim.getConfirmRAt() != null;

        // 如果都确认了走 BOTH_CONFIRMED (1->3)，否则走 CONFIRM (1->1自环)
        String event = bothConfirmed ? "BOTH_CONFIRMED" : "CONFIRM";

        claimStateMachine.transition(claim, event, userId, role, bothConfirmed ? "双方确认完成" : "单方确认");
        claimMapper.updateById(claim);
    }

    // ================= FR-12 管理员裁决 =================
    @Transactional(rollbackFor = Exception.class)
    public void arbitrateVerdict(Long claimId, Long adminId, String verdict, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("裁决必须填写理由");
        }

        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("记录不存在");
        if (claim.getState() != ClaimState.ARBITRATION.code()) {
            throw BizException.conflict("当前状态不在仲裁中，无法裁决");
        }

        // RETURN -> 判归还；REJECT -> 判驳回
        String event;
        if ("RETURN".equals(verdict)) {
            event = "ARBITRATE_RETURN";
        } else if ("REJECT".equals(verdict)) {
            event = "ARBITRATE_REJECT";
        } else {
            throw BizException.badRequest("不支持的裁决结果: " + verdict);
        }

        claimStateMachine.transition(claim, event, adminId, "ADMIN", reason);
        claimMapper.updateById(claim);
    }

    /**
     * 特征列表 -> 合法 JSON 文本。
     * <p>不能用 {@code List.toString()}：它产出的是 {@code [a, b, c]}，
     * 字符串没有引号，MySQL 的 JSON 列会直接报 "Invalid JSON text"。
     */
    private String toJson(List<String> features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            throw BizException.badRequest("特征内容无法序列化");
        }
    }

    /**
     * 上传凭证（FR-06：可附 1 张）。
     * <p>重复上传即替换：文件名换成新的 UUID，旧文件不再被引用。
     * <p>可见期不在这里写死：上传时置空表示"关系存续期内一直可见"，
     * 进入终态时由状态机写入"结束后保留 30 天"的截止时间（KV-03）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void uploadVoucher(Long claimId, Long userId, MultipartFile file) {
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("认领不存在");
        if (!claim.getApplicantId().equals(userId)) throw BizException.forbidden("只有申请人可以上传凭证");
        if (claim.getState() != ClaimState.PENDING.code()) throw BizException.conflict("只能在待处理状态下上传凭证");
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择要上传的凭证");

        try {
            String fileName = voucherStorage.save(file);
            claim.setVoucherUrl(fileName);
            claim.setVoucherVisibleUntil(null); // 由终态迁移再设截止（KV-03）
            claimMapper.updateById(claim);
        } catch (IOException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 读取凭证文件（FR-06 / QR-04）。
     * <p>只有拾得者与管理员能看；关系结束后超过可见期一律 404，
     * 不返回"无权"——避免泄露"这里曾经有张凭证"。
     */
    public Path loadVoucher(Long claimId, Long userId, String role) {
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) throw BizException.notFound("认领记录不存在");
        if (claim.getVoucherUrl() == null) throw BizException.notFound("该申请没有凭证");

        boolean isRespondent = userId != null && userId.equals(claim.getRespondentId());
        boolean isAdmin = "ADMIN".equals(role);
        if (!isRespondent && !isAdmin) {
            throw BizException.forbidden("无权查看此凭证");
        }
        if (!voucherVisible(claim)) {
            throw BizException.notFound("凭证已过可见期");
        }
        try {
            return voucherStorage.resolve(claim.getVoucherUrl());
        } catch (IllegalArgumentException e) {
            throw BizException.notFound("凭证文件不存在");
        }
    }

    /** KV-03：存续期内可见；结束后按 voucher_visible_until 保留一段时间 */
    public static boolean voucherVisible(Claim claim) {
        boolean ended = claim.getState() != null
                && (claim.getState() == ClaimState.COMPLETED.code() || claim.getState() == ClaimState.ENDED.code());
        if (!ended) {
            return true;
        }
        return claim.getVoucherVisibleUntil() != null
                && LocalDateTime.now().isBefore(claim.getVoucherVisibleUntil());
    }

    public record ApplyRequest(Long itemId, List<String> features) {}
}