package edu.campus.lostfound.web;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 转成对应 HTTP 状态码 + 统一响应体。
 * <p>约定：越权一律 403（QR-03），占用冲突 409（FR-07），参数问题 400，未认证 401。
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;

    public BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(HttpStatus.FORBIDDEN, message);
    }

    public static BizException notFound(String message) {
        return new BizException(HttpStatus.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(HttpStatus.CONFLICT, message);
    }
}
