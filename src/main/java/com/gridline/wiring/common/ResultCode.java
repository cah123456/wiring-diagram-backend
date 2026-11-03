package com.gridline.wiring.common;

/**
 * 统一业务状态码。
 *
 * <p>与 HTTP 状态码分工如下：</p>
 * <ul>
 *   <li>HTTP 状态码表达传输/资源语义（200、201、204、400、404、409、500），
 *       供浏览器、网关和监控使用；</li>
 *   <li>本枚举表达业务语义，放在响应体的 {@code code} 字段中，供前端做分支判断。</li>
 * </ul>
 *
 * <p>当前项目尚未接入登录鉴权，{@link #EXPIRE}、{@link #UNAUTHORIZED}、
 * {@link #FORBIDDEN} 先作为预留状态码保留，后续接入鉴权时直接使用，避免前端再次
 * 调整错误码映射。</p>
 */
public enum ResultCode {

    /** 请求成功。所有成功响应固定使用该码，具体业务结果由 {@code data.result} 表达。 */
    SUCCESS(20000, "操作成功"),

    /** 可预期的业务失败，例如资源不存在、版本冲突、图纸数量超限等。 */
    ERROR(20001, "操作失败"),

    /** 登录凭证过期（预留）。 */
    EXPIRE(20002, "登录过期"),

    /** 未登录或缺少凭证（预留）。 */
    UNAUTHORIZED(20003, "未登录"),

    /** 已登录但没有操作权限（预留）。 */
    FORBIDDEN(20004, "无权限"),

    /** 未捕获的服务端异常，通常会在日志中记录完整堆栈。 */
    SYSTEM_ERROR(20005, "系统繁忙，请稍后重试");

    /** 业务状态码，序列化到响应体的 {@code code} 字段。 */
    private final int code;

    /** 默认提示语，可在具体响应中通过 {@link R#message(String)} 覆盖。 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * @return 业务状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * @return 默认提示语
     */
    public String getMessage() {
        return message;
    }
}
