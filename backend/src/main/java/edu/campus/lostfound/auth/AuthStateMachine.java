package edu.campus.lostfound.auth;

import edu.campus.lostfound.audit.AuditRecorder;
import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.web.BizException;
import org.springframework.stereotype.Component;

/**
 * 认证状态机（两态）：任何 auth_state 的写入都必须经过这里。
 *
 * <p>只有两条边：UNVERIFIED --bind--> VERIFIED，VERIFIED --unbind--> UNVERIFIED。
 * 非法跃迁一律拒绝并留痕（FR-04 / FR-09 的口径一致）。
 */
@Component
public class AuthStateMachine {

    private final AuditRecorder auditRecorder;

    public AuthStateMachine(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    /** 0 -> 1 */
    public void bind(Account account, SsoIdentity identity) {
        if (AuthState.of(account.getAuthState()) == AuthState.VERIFIED) {
            throw BizException.conflict("该账号已完成认证，无需重复绑定");
        }

        int fromState = account.getAuthState();

        account.setAuthState(AuthState.VERIFIED.code());
        account.setRealName(identity.realName());
        account.setStudentNoHash(HashUtils.hash256(identity.studentNo()));

        auditRecorder.record("ACCOUNT", account.getUserId(), "BIND",
                String.valueOf(fromState), String.valueOf(account.getAuthState()),
                account.getUserId(), "USER", "{\"reason\":\"SSO绑定\"}");
    }

    /** 1 -> 0 */
    public void unbind(Account account, long operatorId, String role, String reason) {
        if (AuthState.of(account.getAuthState()) == AuthState.UNVERIFIED) {
            throw BizException.conflict("该账号尚未认证，无需解绑");
        }

        int fromState = account.getAuthState();

        account.setAuthState(AuthState.UNVERIFIED.code());
        // 必须置 null 而不是 ""：唯一索引允许多个 NULL，但只允许一个 ""
        account.setStudentNoHash(null);

        auditRecorder.record("ACCOUNT", account.getUserId(), "UNBIND",
                String.valueOf(fromState), String.valueOf(account.getAuthState()),
                operatorId, role, "{\"reason\":\"" + reason + "\"}");
    }
}
