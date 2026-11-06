package edu.campus.lostfound.auth;

import lombok.Getter;

public enum AuthState {
    UNVERIFIED(0, "未认证"),
    VERIFIED(1, "已认证");

    private final int code;
    @Getter
    private final String description;

    AuthState(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return this.code;
    }

    public static AuthState of(int code) {
        for (AuthState state : AuthState.values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("非法的认证状态码: " + code);
    }
}