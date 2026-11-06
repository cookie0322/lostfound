package edu.campus.lostfound.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 学号哈希：库表只存 SHA-256 小写十六进制（CHAR(64)），不存明文（QR-02）。
 *
 * <p>目前只有认证模块用得到，所以放在 auth 包；等 F2 也需要时再考虑上移
 * —— 与架构评审 D-02 判据三一致：没有第二个调用方就不抽公共层。
 */
public final class HashUtils {

    private HashUtils() {
    }

    public static String hash256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JRE 必须实现的算法，走到这里说明运行环境不完整
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}