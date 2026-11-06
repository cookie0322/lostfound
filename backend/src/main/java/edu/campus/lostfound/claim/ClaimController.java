package edu.campus.lostfound.claim;

import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.web.ApiResult;
import edu.campus.lostfound.web.BizException;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimAppService claimAppService;

    public ClaimController(ClaimAppService claimAppService) {
        this.claimAppService = claimAppService;
    }

    // 认领申请
    @PostMapping
    public ApiResult<ClaimView> apply(@RequestBody ClaimAppService.ApplyRequest req, HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");

        return ApiResult.ok(claimAppService.apply(userId, req, role));
    }

    // 详情
    @GetMapping("/{id}")
    public ApiResult<ClaimView> detail(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");

        return ApiResult.ok(claimAppService.getDetail(id, userId, role));
    }

    // 我的
    @GetMapping("/mine")
    public ApiResult<Map<String, List<ClaimView>>> mine(HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");

        return ApiResult.ok(claimAppService.getMine(userId, role));
    }

    public record ReviewRequest(String action, String reason) {}
    public record WithdrawRequest(boolean confirm) {}
    public record ArbitrateRequest(String reason) {}

    // 拾得者验核
    @PostMapping("/{id}/review")
    public ApiResult<Void> review(@PathVariable Long id, @RequestBody ReviewRequest req, HttpSession session) {
        Long userId = currentUserId(session);
        claimAppService.review(id, userId, req.action(), req.reason());
        return ApiResult.ok();
    }

    // 撤销
    @PostMapping("/{id}/withdraw")
    public ApiResult<Void> withdraw(@PathVariable Long id, @RequestBody WithdrawRequest req, HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");
        claimAppService.withdraw(id, userId, role, req.confirm());
        return ApiResult.ok();
    }

    // 转仲裁
    @PostMapping("/{id}/arbitrate")
    public ApiResult<Void> arbitrate(@PathVariable Long id, @RequestBody ArbitrateRequest req, HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");
        claimAppService.arbitrate(id, userId, role, req.reason());
        return ApiResult.ok();
    }

    // 确认
    @PostMapping("/{id}/confirm")
    public ApiResult<Void> confirm(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");
        claimAppService.confirm(id, userId, role);
        return ApiResult.ok();
    }

    // 上传凭据
    @PostMapping("/{id}/voucher")
    public ApiResult<Void> uploadVoucher(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file,
                                         HttpSession session) {
        Long userId = currentUserId(session);
        claimAppService.uploadVoucher(id, userId, file);
        return ApiResult.ok();
    }

    /**
     * 查看凭据（FR-06：内容仅拾得者与管理员可见）。
     * <p>返回文件流而不是 JSON：图片由前端 &lt;img src&gt; 直接引用。
     */
    @GetMapping("/{id}/voucher")
    public ResponseEntity<Resource> downloadVoucher(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        String role = (String) session.getAttribute("CURRENT_ROLE");
        Path path = claimAppService.loadVoucher(id, userId, role);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    private Long currentUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute("CURRENT_USER_ID");
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return userId;
    }
}