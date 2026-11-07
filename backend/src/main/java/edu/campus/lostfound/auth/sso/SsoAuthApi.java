package edu.campus.lostfound.auth.sso;

import edu.campus.lostfound.auth.AuthApi;
import edu.campus.lostfound.auth.SsoIdentity;
import edu.campus.lostfound.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 学校统一身份认证的**待接入实现**（QR-05 的替换验证用）。
 *
 * <p>与 {@code LocalAuthApi} 相比，它换了一套完全不同的票据协议：
 * 票据是 {@code base64url(学号|姓名|HMAC-SHA256 签名)}，由认证平台用共享密钥签发，
 * 后端只做验签——这正是真实 CAS / OAuth 回跳后的典型做法。
 *
 * <p>切换方式只有两处：新增本文件 + 把 {@code lf.auth.mode} 改成 {@code sso}。
 * 业务代码（BindingAppService、AuthStateMachine、Controller）一行都不用改，
 * 因为它们只依赖 {@link AuthApi} 接口——这就是 QR-05 的验收口径。
 *
 * <p>学校接口获批后，把 {@link #exchange} 换成真实的 HTTP 调用即可。
 */
@Component
@ConditionalOnProperty(name = "lf.auth.mode", havingValue = "sso")
public class SsoAuthApi implements AuthApi {

    private final String sharedSecret;

    public SsoAuthApi(@Value("${lf.auth.sso.shared-secret:dev-secret}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @Override
    public SsoIdentity exchange(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw BizException.unauthorized("票据无效");
        }

        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(ticket), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw BizException.unauthorized("票据格式不正确");
        }

        String[] parts = decoded.split("\\|");
        if (parts.length != 3) {
            throw BizException.unauthorized("票据格式不正确");
        }

        String studentNo = parts[0];
        String realName = parts[1];
        String signature = parts[2];

        String expected = sign(studentNo + "|" + realName);
        if (!constantTimeEquals(expected, signature)) {
            throw BizException.unauthorized("票据签名校验失败");
        }
        return new SsoIdentity(studentNo, realName);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /** 定长比较，避免按字节短路带来的时间侧信道 */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
