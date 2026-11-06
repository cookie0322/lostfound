package edu.campus.lostfound.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.lostfound.audit.AuditRecorder;
import edu.campus.lostfound.data.entity.Appeal;
import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.data.mapper.AppealMapper;
import edu.campus.lostfound.data.mapper.AccountMapper;
import edu.campus.lostfound.web.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学号占用申诉（FR-03 的申诉入口 + FR-05 的处理）。
 *
 * <p>流程：用户提交（学号 + 理由）→ 管理员看到待处理列表 → 核实后选择
 * "解除原绑定"或"驳回"。解除时复用 {@link AuthStateMachine#unbind}，
 * 所以审计与状态语义和用户自己解绑完全一致。
 */
@Service
public class AppealAppService {

    private final AppealMapper appealMapper;
    private final AccountMapper accountMapper;
    private final AuthStateMachine authStateMachine;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AppealAppService(AppealMapper appealMapper, AccountMapper accountMapper,
                            AuthStateMachine authStateMachine, AuditRecorder auditRecorder,
                            ObjectMapper objectMapper) {
        this.appealMapper = appealMapper;
        this.accountMapper = accountMapper;
        this.authStateMachine = authStateMachine;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    /** 用户提交申诉 */
    @Transactional(rollbackFor = Exception.class)
    public Appeal submit(long userId, String studentNo, String reason) {
        if (studentNo == null || studentNo.isBlank()) {
            throw BizException.badRequest("请填写被占用的学号");
        }
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("请填写申诉理由");
        }

        String hash = HashUtils.hash256(studentNo.trim());
        Account occupied = accountMapper.selectOne(
                new LambdaQueryWrapper<Account>().eq(Account::getStudentNoHash, hash));
        if (occupied == null) {
            throw BizException.badRequest("该学号目前没有被绑定，请直接完成认证");
        }
        if (occupied.getUserId() == userId) {
            throw BizException.badRequest("该学号属于当前账号，无需申诉");
        }

        Long pending = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getUserId, userId)
                .eq(Appeal::getStudentNoHash, hash)
                .eq(Appeal::getState, 0));
        if (pending != null && pending > 0) {
            throw BizException.conflict("你已提交过同一学号的申诉，请等待处理");
        }

        Appeal appeal = new Appeal();
        appeal.setUserId(userId);
        appeal.setStudentNoHash(hash);
        appeal.setReason(reason.trim());
        appeal.setState(0);
        appeal.setCreatedAt(LocalDateTime.now());
        appealMapper.insert(appeal);

        auditRecorder.record("APPEAL", appeal.getAppealId(), "APPEAL_SUBMIT", null, "0",
                userId, "USER", json(Map.of("reason", appeal.getReason())));
        return appeal;
    }

    /** 管理员看待处理申诉 */
    public List<Appeal> listPending() {
        return appealMapper.selectList(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getState, 0)
                .orderByAsc(Appeal::getCreatedAt));
    }

    /**
     * 管理员处理申诉。
     *
     * @param action UNBIND（申诉成立，解除原绑定）/ REJECT（驳回）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resolve(long adminId, long appealId, String action, String reason) {
        if (!"UNBIND".equals(action) && !"REJECT".equals(action)) {
            throw BizException.badRequest("不支持的处理方式: " + action);
        }
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw BizException.notFound("申诉不存在");
        }
        if (appeal.getState() == null || appeal.getState() != 0) {
            throw BizException.conflict("该申诉已处理，不能重复处理");
        }

        if ("UNBIND".equals(action)) {
            Account occupied = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                    .eq(Account::getStudentNoHash, appeal.getStudentNoHash()));
            if (occupied == null) {
                throw BizException.conflict("该学号已不再被绑定，无需解除");
            }
            authStateMachine.unbind(occupied, adminId, "ADMIN",
                    "申诉成立，管理员解除原绑定。理由：" + (reason == null ? "" : reason));
            accountMapper.updateById(occupied);
            appeal.setState(1);
        } else {
            appeal.setState(2);
        }

        appeal.setHandledBy(adminId);
        appeal.setHandledAt(LocalDateTime.now());
        appealMapper.updateById(appeal);

        auditRecorder.record("APPEAL", appealId, "APPEAL_" + action, "0",
                String.valueOf(appeal.getState()), adminId, "ADMIN",
                json(Map.of("reason", reason == null ? "" : reason)));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
