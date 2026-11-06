package edu.campus.lostfound.auth;

import edu.campus.lostfound.data.entity.Account;

/**
 * 账号对外视图：只出脱敏后的字段，杜绝明文姓名外泄（QR-04）。
 * 所有对外接口（login / bind / me）一律返回它，不返回 Account 实体。
 */
public record AccountView(long userId,
                          String loginName,
                          String realNameMasked,
                          int authState,
                          String role) {

    /** 实体 -> 视图，脱敏在这里做，别散落到各个 Controller */
    public static AccountView of(Account a) {
        return new AccountView(
                a.getUserId(),
                a.getLoginName(),
                maskName(a.getRealName()),
                a.getAuthState() == null ? 0 : a.getAuthState(),
                a.getRole());
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        // 张同学 -> 张**
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}