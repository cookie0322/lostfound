package edu.campus.lostfound.web; // 或者 web.admin 包

import edu.campus.lostfound.auth.AccountView;
import edu.campus.lostfound.auth.AppealAppService;
import edu.campus.lostfound.auth.BindingAppService;
import edu.campus.lostfound.claim.ClaimAppService;
import edu.campus.lostfound.auth.AuthController;
import edu.campus.lostfound.data.entity.Appeal;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin") // 管理端前缀
public class AdminController {

    private final BindingAppService bindingAppService;
    private final ClaimAppService claimAppService;
    private final AppealAppService appealAppService;

    public AdminController(BindingAppService bindingAppService, ClaimAppService claimAppService,
                           AppealAppService appealAppService) {
        this.bindingAppService = bindingAppService;
        this.claimAppService = claimAppService;
        this.appealAppService = appealAppService;
    }

    /** 待处理申诉列表（FR-05） */
    @RequireRole("ADMIN")
    @GetMapping("/appeals")
    public ApiResult<List<Appeal>> appeals() {
        return ApiResult.ok(appealAppService.listPending());
    }

    public record ResolveAppealRequest(String action, String reason) {}
    /** 处理申诉：UNBIND 解除原绑定 / REJECT 驳回（FR-05） */
    @RequireRole("ADMIN")
    @PostMapping("/appeals/{id}/resolve")
    public ApiResult<Void> resolveAppeal(@PathVariable Long id,
                                         @RequestBody ResolveAppealRequest req,
                                         HttpSession session) {
        appealAppService.resolve(currentUserId(session), id, req.action(), req.reason());
        return ApiResult.ok();
    }

    // 强制账号解绑
    @RequireRole("ADMIN")
    @PostMapping("/appeal/{userId}/unbind")
    public ApiResult<AccountView> adminUnbind(@PathVariable Long userId, @RequestBody AuthController.UnbindRequest req, HttpSession session) {
        Long adminId = currentUserId(session);
        return ApiResult.ok(bindingAppService.adminUnbind(adminId, userId, req.confirm()));
    }

    // 认领裁决
    public record VerdictRequest(String verdict, String reason) {}

    /** 按状态列出认领（?state=2 取仲裁中的单子） */
    @RequireRole("ADMIN")
    @GetMapping("/claims")
    public ApiResult<List<edu.campus.lostfound.claim.ClaimView>> claims(
            @RequestParam(required = false) Integer state) {
        return ApiResult.ok(claimAppService.listByState(state));
    }

    @RequireRole("ADMIN")
    @PostMapping("/claims/{id}/verdict")
    public ApiResult<Void> verdict(@PathVariable Long id, @RequestBody VerdictRequest req, HttpSession session) {
        Long adminId = currentUserId(session);
        claimAppService.arbitrateVerdict(id, adminId, req.verdict(), req.reason());
        return ApiResult.ok();
    }

    private Long currentUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute("CURRENT_USER_ID");
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return userId;
    }
}