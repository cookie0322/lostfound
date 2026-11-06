package edu.campus.lostfound.auth.mock;

import edu.campus.lostfound.web.ApiResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模拟"学校认证页"：真实流程是浏览器跳转过去输账号密码，带票据回跳；
 * 开发期用这个接口直接发一张票，替代那两次跳转（图附 2-3 的第 2~4 步）。
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "lf.auth.mode", havingValue = "mock", matchIfMissing = true)
public class LocalAuthController {

    public record MockSsoRequest(String studentNo, String realName) {
    }

    private final LocalAuthApi localAuthApi;

    public LocalAuthController(LocalAuthApi localAuthApi) {
        this.localAuthApi = localAuthApi;
    }

    @PostMapping("/mock-sso")
    public ApiResult<Map<String, String>> mockSso(@RequestBody MockSsoRequest request) {
        String ticket = localAuthApi.issue(request.studentNo(), request.realName());
        return ApiResult.ok(Map.of("ticket", ticket));
    }
}
