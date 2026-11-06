package edu.campus.lostfound.auth;

public interface AuthApi {

    /**
     * 用票据换取学号与姓名
     * @param ticket 学校认证系统返回的票据
     * @return SsoIdentity 包含学号和姓名的对象
     */
    SsoIdentity exchange(String ticket);   // 票据换学号+姓名；失败抛 401
}