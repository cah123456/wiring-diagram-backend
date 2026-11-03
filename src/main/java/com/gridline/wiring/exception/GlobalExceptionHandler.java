package com.gridline.wiring.exception;

import com.gridline.wiring.common.R;
import com.gridline.wiring.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器。
 *
 * <p>把抛出 Controller 的异常统一转换为 {@link R} 响应体，同时保留准确的 HTTP 状态码。
 * 前端既可以按 HTTP 状态码做通用错误拦截，也可以读取 {@code message} 展示提示、
 * 读取 {@code data.fields} 定位具体校验字段。</p>
 *
 * <p>处理优先级由 Spring 按最具体的异常类型选择，兜底为 {@link Exception}。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，使用异常自带 HTTP 状态码与业务状态码。
     *
     * @param ex      业务异常
     * @param request 当前请求，仅用于日志定位
     * @return 带真实 HTTP 状态码的 R 响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R> handleBusinessException(BusinessException ex,
                                                      HttpServletRequest request) {
        log.debug("Business exception at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(R.error(ex.getResultCode()).message(ex.getMessage()));
    }

    /**
     * 处理请求参数校验失败。
     *
     * <p>同一字段可能触发多条约束，这里只保留第一条提示。字段错误放在
     * {@code data.fields}，前端表单可以按字段名精确标红。</p>
     *
     * @param ex      校验异常
     * @param request 当前请求
     * @return HTTP 400 + 字段错误映射
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R> handleValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        log.debug("Validation failed at {}: {}", request.getRequestURI(), fields);
        return ResponseEntity.badRequest()
                .body(R.error().message("提交的数据校验未通过")
                        .data("fields", fields));
    }

    /**
     * 处理无法解析的请求体，例如 JSON 语法错误、字段类型不匹配、请求体为空。
     *
     * @param ex      解析异常
     * @param request 当前请求
     * @return HTTP 400 + 通用提示
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R> handleUnreadable(HttpMessageNotReadableException ex,
                                              HttpServletRequest request) {
        log.warn("Unreadable request body at {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(R.error().message("请求内容格式错误，请检查 JSON 数据"));
    }

    /**
     * 处理不存在的接口路径，避免落入 500 兜底。
     *
     * @param ex      未找到处理器的异常
     * @param request 当前请求
     * @return HTTP 404 + R 错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R> handleNoHandler(NoHandlerFoundException ex,
                                             HttpServletRequest request) {
        log.debug("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.error().message("请求的接口不存在"));
    }

    /**
     * 处理静态资源不存在。关闭资源映射后，少数路径由资源处理器抛出该异常。
     *
     * @param ex      资源不存在异常
     * @param request 当前请求
     * @return HTTP 404 + R 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R> handleNoResource(NoResourceFoundException ex,
                                              HttpServletRequest request) {
        log.debug("No resource for {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.error().message("请求的资源不存在"));
    }

    /**
     * 处理 HTTP 方法不匹配，例如对只读接口发送 POST。
     *
     * @param ex      方法不支持异常
     * @param request 当前请求
     * @return HTTP 405 + R 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                      HttpServletRequest request) {
        log.debug("Method {} not supported at {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.error().message("请求方法不支持"));
    }

    /**
     * 处理缺失查询参数或参数类型不匹配，例如把页码传成非数字。
     *
     * @param ex      参数异常
     * @param request 当前请求
     * @return HTTP 400 + R 错误响应
     */
    @ExceptionHandler({
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<R> handleBadParameter(Exception ex, HttpServletRequest request) {
        log.debug("Bad request parameter at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(R.error().message("请求参数缺失或格式错误"));
    }

    /**
     * 处理数据库约束冲突，例如图纸编号唯一键碰撞。
     *
     * @param ex      数据完整性异常
     * @param request 当前请求
     * @return HTTP 409 + 通用提示
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R> handleDataIntegrity(DataIntegrityViolationException ex,
                                                 HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.error().message("数据冲突，请刷新后重试"));
    }

    /**
     * 兜底处理未预期异常。记录完整堆栈，但响应体不泄露内部信息。
     *
     * @param ex      未预期异常
     * @param request 当前请求
     * @return HTTP 500 + R 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error(ResultCode.SYSTEM_ERROR));
    }
}
