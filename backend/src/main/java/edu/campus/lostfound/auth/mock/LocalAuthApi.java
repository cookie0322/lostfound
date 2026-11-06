package edu.campus.lostfound.auth.mock;

import edu.campus.lostfound.auth.AuthApi;
import edu.campus.lostfound.auth.SsoIdentity;
import edu.campus.lostfound.web.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地模拟的统一身份认证实现（KV-01 的临时替身，对应 3.2"开发期由本地模拟登录提供"）。
 *
 * <p>真实 SSO 时，票据由学校认证页签发、后端只负责"用票据换取学号与姓名"。
 * 这里把签发也一起模拟了，但保留两条关键语义（图附 2-3）：
 * 票据**一次性**（exchange 里先 remove 再判断）与**短时有效**（60 秒）。
 *
 * <p>切到真实 SSO 时：新增一个实现 AuthApi 的类，把 lf.auth.mode 改成 sso，
 * 业务代码一行不用动（QR-05）。
 */
@Component
@ConditionalOnProperty(name = "lf.auth.mode", havingValue = "mock", matchIfMissing = true)
public class LocalAuthApi implements AuthApi {

    private static final long TTL_MS = 60_000L;

    private record Ticket(String studentNo, String realName, long expireAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    /** 模拟"学校认证页"签发一张票据 */
    public String issue(String studentNo, String realName) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Ticket(studentNo, realName, System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    @Override
    public SsoIdentity exchange(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw BizException.unauthorized("票据无效");
        }
        // 先移除再判断：否则并发下同一张票能被消费两次
        Ticket t = tickets.remove(ticket);
        if (t == null) {
            throw BizException.unauthorized("票据无效或已被使用，请重新认证");
        }
        if (t.expireAt() < System.currentTimeMillis()) {
            throw BizException.unauthorized("票据已过期，请重新认证");
        }
        return new SsoIdentity(t.studentNo(), t.realName());
    }
}
