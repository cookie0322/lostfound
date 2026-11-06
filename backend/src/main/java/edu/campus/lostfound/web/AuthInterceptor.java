package edu.campus.lostfound.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    // 【改动点1】：删掉 AccountMapper，连构造函数都不需要了！

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

        if (requireRole == null) {
            return true;
        }

        // 【改动点2】：不再查库，直接从 Session 拿数据
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw BizException.unauthorized("请先登录");
        }

        Long userId = (Long) session.getAttribute("CURRENT_USER_ID");
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }

        // 获取 Session 里的角色
        String currentRole = (String) session.getAttribute("CURRENT_ROLE");

        // 比对角色
        if (currentRole == null || !requireRole.value().equals(currentRole)) {
            throw BizException.forbidden("无权访问，需要 " + requireRole.value() + " 角色");
        }

        return true;
    }
}