package com.gridline.wiring.common;

/**
 * 统一业务状态码。
 *
 * <p>HTTP 状态码表达传输/资源语义（400、404、405、500 等），本枚举表达业务语义，
 * 放在响应体的 {@code code} 字段中，供前端做分支判断。</p>
 *
 * <p>当前项目尚未接入登录鉴权，{@link #EXPIRE}、{@link #UNAUTHORIZED}、
 * {@link #FORBIDDEN} 作为预留状态码保留。</p>
 */
public enum ResultCode {

    /** 请求成功。所有成功响应固定使用该码。 */
    SUCCESS(20000, "操作成功"),

    /** 可预期的业务失败，例如资源不存在、参数不合法、数据冲突等。 */
    ERROR(20001, "操作失败"),

    /** 登录凭证过期（预留）。 */
    EXPIRE(20002, "登录过期"),

    /** 未登录或缺少凭证（预留）。 */
    UNAUTHORIZED(20003, "未登录"),

    /** 已登录但没有操作权限（预留）。 */
    FORBIDDEN(20004, "无权限"),

    /** 未捕获的服务端异常，通常会在日志中记录完整堆栈。 */
    SYSTEM_ERROR(20005, "系统繁忙，请稍后重试");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
