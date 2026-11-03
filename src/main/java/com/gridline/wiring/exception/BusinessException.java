package com.gridline.wiring.exception;

import com.gridline.wiring.common.ResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常。
 *
 * <p>Service 层遇到“可预期”的错误（图纸不存在、文档格式错误等）时抛出本异常，
 * 由 {@link GlobalExceptionHandler} 统一转换为 R 响应。异常同时携带 HTTP 状态码和
 * 业务状态码，使响应语义完整：</p>
 *
 * <ul>
 *   <li>{@link #notFound(String)} → HTTP 404</li>
 *   <li>{@link #badRequest(String)} → HTTP 400</li>
 *   <li>{@link #conflict(String)} → HTTP 409</li>
 *   <li>{@link #systemError(String)} → HTTP 500</li>
 * </ul>
 *
 * <p>异常消息会原样展示给用户，因此应使用简洁的中文描述，不要拼接 SQL、类名或堆栈。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 随异常一起返回给客户端的 HTTP 状态码。 */
    private final HttpStatus status;

    /** 响应体中的业务状态码。 */
    private final ResultCode resultCode;

    public BusinessException(HttpStatus status, ResultCode resultCode, String message) {
        super(message);
        this.status = status;
        this.resultCode = resultCode;
    }

    /**
     * 创建 404 异常，用于“资源不存在”场景。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, ResultCode.ERROR, message);
    }

    /**
     * 创建 400 异常，用于“请求本身不合法”场景。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, ResultCode.ERROR, message);
    }

    /**
     * 创建 409 异常，用于并发写入、唯一键冲突等场景。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, ResultCode.ERROR, message);
    }

    /**
     * 创建 500 异常，用于服务端数据损坏等不应由用户修复的问题。
     *
     * @param message 错误提示
     * @return 业务异常实例
     */
    public static BusinessException systemError(String message) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                ResultCode.SYSTEM_ERROR, message);
    }
}
