package edu.campus.lostfound.auth;

import edu.campus.lostfound.web.ApiResult;
import edu.campus.lostfound.web.BizException;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String loginName) {}
    public record BindRequest(String ticket) {}
    public record UnbindRequest(boolean confirm) {}
    public record AppealRequest(String studentNo, String reason) {}


    private final BindingAppService bindingAppService;
    private final AppealAppService appealAppService;

    public AuthController(BindingAppService bindingAppService, AppealAppService appealAppService) {
        this.bindingAppService = bindingAppService;
        this.appealAppService = appealAppService;
    }

    /** 1. 模拟"既有登录模块" */
    @PostMapping("/login")
    public ApiResult<AccountView> login(@RequestBody LoginRequest request, HttpSession session) {
        return ApiResult.ok(bindingAppService.login(request.loginName(), session));
    }

    /** 2. 用票据绑定学号 */
    @PostMapping("/bind")
    public ApiResult<AccountView> bind(@RequestBody BindRequest request, HttpSession session) {
        return ApiResult.ok(bindingAppService.bind(currentUserId(session), request.ticket()));
    }

    /** 3. 解绑 */
    // 用户解绑
    @PostMapping("/unbind")
    public ApiResult<AccountView> unbind(@RequestBody UnbindRequest req, HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResult.ok(bindingAppService.unbind(userId, req.confirm()));
    }


    /** 4. 查我 */
    @GetMapping("/me")
    public ApiResult<AccountView> me(HttpSession session) {
        return ApiResult.ok(bindingAppService.me(currentUserId(session)));
    }

    /** 5. 申诉：学号被他人占用（FR-03 提示的入口 / FR-05） */
    @PostMapping("/appeal")
    public ApiResult<Long> appeal(@RequestBody AppealRequest req, HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResult.ok(appealAppService.submit(userId, req.studentNo(), req.reason()).getAppealId());
    }

    /** 未登录一律 401，不能是 500（QR-03 的越权矩阵要能区分 401 与 403） */
    private Long currentUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute("CURRENT_USER_ID");
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return userId;
    }
}
