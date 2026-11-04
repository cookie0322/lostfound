package edu.campus.lostfound.web;

/**
 * 统一响应体。
 * <p>code = 0 表示成功；失败时 code 取 HTTP 状态码，便于前端按 401/403/409 分别处理
 * （对应 QR-03 越权 403、FR-07 占用冲突 409）。
 */
public record ApiResult<T>(int code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
