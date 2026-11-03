package com.gridline.wiring.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * <p>职责是把握出 Controller 的异常统一转换为 {@link R} 响应体，同时保留准确的
 * HTTP 状态码。这样前端既可以按 HTTP 状态码做通用错误拦截，也可以直接读取
 * {@code message} 展示提示、读取 {@code data.fields} 定位具体校验字段。</p>
 *
 * <p>处理优先级（Spring 会选择最具体的异常类型）：</p>
 * <ol>
 *   <li>{@link ApiException}：业务代码主动抛出的可预期异常（404/400/409）；</li>
 *   <li>{@link MethodArgumentNotValidException}：Bean Validation 校验失败；</li>
 *   <li>{@link HttpMessageNotReadableException}：请求体不是合法 JSON；</li>
 *   <li>{@link NoHandlerFoundException} / {@link NoResourceFoundException}：请求了
 *       不存在的接口或资源（404）；</li>
 *   <li>{@link HttpRequestMethodNotSupportedException}：HTTP 方法不支持（405）；</li>
 *   <li>{@link MissingServletRequestParameterException} /
 *       {@link MethodArgumentTypeMismatchException}：参数缺失或类型错误（400）；</li>
 *   <li>{@link DataIntegrityViolationException}：数据库唯一键、外键等约束冲突；</li>
 *   <li>{@link Exception}：兜底异常，记录完整堆栈并返回 500。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * <p>{@link ApiException} 携带 HTTP 状态码和面向用户的提示语，因此直接把提示语
     * 放入 R.message，业务状态码使用通用的 {@link ResultCode#ERROR}。服务端不再向
     * 前端暴露堆栈或内部实现细节。</p>
     *
     * @param ex      业务异常
     * @param request 当前请求，仅用于日志定位，不写入响应体
     * @return 带真实 HTTP 状态码的 R 响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<R> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Business exception at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(R.error().message(ex.getMessage()));
    }

    /**
     * 处理请求参数校验失败。
     *
     * <p>同一字段可能触发多条约束，这里使用 {@code putIfAbsent} 只保留第一条提示，
     * 避免前端展示重复信息。字段错误放在 {@code data.fields}，前端表单可以按字段名
     * 精确标红。</p>
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
     * 处理无法解析的请求体。
     *
     * <p>常见原因包括 JSON 语法错误、字段类型不匹配、请求体为空等。具体解析细节可能
     * 包含类名和字段路径，只写入服务端日志，不返回给前端。</p>
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
     * 处理不存在的接口路径。
     *
     * <p>Spring MVC 找不到映射时抛出 {@link NoHandlerFoundException}。如果不单独
     * 处理，它会被最后的 {@link Exception} 兜底方法误判为系统异常并返回 500。
     * 这里明确返回 404，同时仍然使用 R 响应体。</p>
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
     * 处理静态资源不存在。
     *
     * <p>当关闭静态资源映射后，少数路径仍可能由资源处理器抛出该异常。它与
     * {@link NoHandlerFoundException} 语义相同，都返回 404，避免落入 500 兜底。</p>
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
     * <p>这类错误属于客户端输入问题，必须返回 400 而不是 500。</p>
     *
     * @param ex      参数异常
     * @param request 当前请求
     * @return HTTP 400 + R 错误响应
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<R> handleBadParameter(Exception ex, HttpServletRequest request) {
        log.debug("Bad request parameter at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(R.error().message("请求参数缺失或格式错误"));
    }

    /**
     * 处理数据库约束冲突。
     *
     * <p>例如并发创建同名图纸触发唯一索引、图纸仍被版本记录引用等。返回 409 让前端
     * 提示用户刷新后重试，而不是误报 500。</p>
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
     * 兜底处理未预期异常。
     *
     * <p>必须记录完整堆栈以便排查。响应体只给出通用提示，避免泄露 SQL、类名、路径等
     * 内部信息；业务状态码使用 {@link ResultCode#SYSTEM_ERROR}。</p>
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
