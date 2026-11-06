package edu.campus.lostfound.claim;

import edu.campus.lostfound.auth.AuthState;

public enum ClaimState {
    PENDING(0),      // 待处理
    HANDOVER(1),     // 待交接
    ARBITRATION(2),  // 仲裁中
    COMPLETED(3),    // 已完成
    ENDED(4);        // 已结束

    private final int code;
    ClaimState(int code) { this.code = code; }
    public int code() { return code; }

    public static ClaimState of(int code) {
        for (ClaimState state : ClaimState.values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("非法的认证状态码: " + code);
    }
}