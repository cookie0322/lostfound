package edu.campus.lostfound.web;

import edu.campus.lostfound.audit.AuditRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 越权拦截：只有标了 {@link RequireRole} 的接口才校验，避免给每个方法写重复代码。
 *
 * <p>角色从 Session 取，不查库——表示层不直接访问数据（架构 3.3.3 红线①），
 * 也顺便让每个请求少一次数据库往返（QR-01）。
 * <p>QR-03 要求"越权一律 403 **并写审计**"，所以拒绝时要落一条 audit_log，
 * 否则事后查不到"谁试图访问了不该访问的接口"。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String CURRENT_USER_ID = "CURRENT_USER_ID";
    private static final String CURRENT_ROLE = "CURRENT_ROLE";

    private final AuditRecorder auditRecorder;

    public AuthInterceptor(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(CURRENT_USER_ID);
        if (userId == null) {
            // 未登录不是"越权"，不写审计（否则任何人都能刷审计表）
            throw BizException.unauthorized("请先登录");
        }

        String currentRole = (String) session.getAttribute(CURRENT_ROLE);
        if (currentRole == null || !requireRole.value().equals(currentRole)) {
            auditRecorder.record("ACCOUNT", userId, "ACCESS_DENIED", currentRole, requireRole.value(),
                    userId, currentRole == null ? "UNKNOWN" : currentRole,
                    "{\"path\":\"" + request.getRequestURI() + "\"}");
            throw BizException.forbidden("无权访问，需要 " + requireRole.value() + " 角色");
        }
        return true;
    }
}
