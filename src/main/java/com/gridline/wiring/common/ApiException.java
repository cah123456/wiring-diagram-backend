package com.gridline.wiring.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。
 *
 * <p>Service 层遇到“可预期”的错误（图纸不存在、版本冲突、数据超限等）时抛出本异常，
 * 由 {@link GlobalExceptionHandler} 统一转换为 {@link R} 响应。与非受检异常的普通
 * {@link RuntimeException} 不同，本异常显式携带 HTTP 状态码，使接口语义更准确：</p>
 *
 * <ul>
 *   <li>{@link #notFound(String)} → HTTP 404</li>
 *   <li>{@link #badRequest(String)} → HTTP 400</li>
 *   <li>{@link #conflict(String)} → HTTP 409</li>
 * </ul>
 *
 * <p>异常消息会原样展示给用户，因此应使用简洁的中文描述，不要拼接 SQL、类名或
 * 堆栈信息。</p>
 */
public class ApiException extends RuntimeException {

    /** 随异常一起返回给客户端的 HTTP 状态码。 */
    private final HttpStatus status;

    /**
     * @param status  HTTP 状态码
     * @param message 面向用户的错误提示
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * @return HTTP 状态码
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 创建 404 异常，用于“资源不存在”场景。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 创建 400 异常，用于“请求本身不合法”场景。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 创建 409 异常，用于乐观锁版本冲突、并发修改等场景。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
