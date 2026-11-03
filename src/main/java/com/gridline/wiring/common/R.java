package com.gridline.wiring.common;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 API 响应包装类。
 *
 * <p>后端所有接口（成功、失败、校验异常、系统异常）都以本类型返回，前端只按同一套
 * 结构解析。序列化后的 JSON 结构如下：</p>
 *
 * <pre>{@code
 * {
 *   "code": 20000,
 *   "message": "操作成功",
 *   "success": true,
 *   "data": {
 *     "result": { ... }   // 业务数据；无返回数据时为空对象
 *   }
 * }
 * }</pre>
 *
 * <p>{@link #data} 使用 {@link LinkedHashMap} 保证输出顺序稳定。业务对象统一放在
 * {@code data.result} 下；参数校验失败时额外放入 {@code data.fields}。</p>
 *
 * <p>本类只用于服务端向前端输出，不参与请求反序列化。构造器保持私有，统一通过
 * {@link #ok()} / {@link #error()} 工厂方法创建，避免出现 success 与 code 不一致。</p>
 */
@Data
public class R {

    /** 业务状态码，见 {@link ResultCode}。 */
    private Integer code;

    /** 面向用户的提示信息。 */
    private String message;

    /** 是否成功；成功响应该字段恒为 {@code true}。 */
    private Boolean success;

    /** 业务数据容器；无数据时保持为空 Map，保证响应结构稳定。 */
    private Map<String, Object> data = new LinkedHashMap<>();

    /** 私有构造器：外部必须通过工厂方法创建。 */
    private R() {
    }

    /**
     * 创建成功响应，默认提示“操作成功”。
     *
     * @return 成功且 data 为空对象的响应
     */
    public static R ok() {
        R r = new R();
        r.setSuccess(true);
        r.setMessage(ResultCode.SUCCESS.getMessage());
        r.setCode(ResultCode.SUCCESS.getCode());
        return r;
    }

    /**
     * 创建带自定义提示语的成功响应。
     *
     * @param message 自定义提示语；为空时回退到默认提示
     * @return 成功响应
     */
    public static R ok(String message) {
        R r = ok();
        if (message != null && !message.isBlank()) {
            r.setMessage(message);
        }
        return r;
    }

    /**
     * 创建通用失败响应（业务状态码 20001）。
     *
     * @return 失败且 data 为空对象的响应
     */
    public static R error() {
        R r = new R();
        r.setSuccess(false);
        r.setMessage(ResultCode.ERROR.getMessage());
        r.setCode(ResultCode.ERROR.getCode());
        return r;
    }

    /**
     * 创建带自定义提示语的失败响应。
     *
     * @param message 失败原因，建议直接使用可展示给用户的中文描述
     * @return 失败响应
     */
    public static R error(String message) {
        R r = error();
        if (message != null && !message.isBlank()) {
            r.setMessage(message);
        }
        return r;
    }

    /**
     * 创建指定业务状态码的失败响应。
     *
     * @param resultCode 状态码枚举，不能为空
     * @return 失败响应
     */
    public static R error(ResultCode resultCode) {
        R r = error();
        if (resultCode != null) {
            r.setCode(resultCode.getCode());
            r.setMessage(resultCode.getMessage());
        }
        return r;
    }

    /**
     * 设置业务状态码及对应提示语。不会改变 success，通常只用于失败响应。
     *
     * @param resultCode 状态码枚举
     * @return 当前响应，便于链式调用
     */
    public R code(ResultCode resultCode) {
        if (resultCode != null) {
            this.setCode(resultCode.getCode());
            this.setMessage(resultCode.getMessage());
        }
        return this;
    }

    /**
     * 覆盖提示语，并返回当前对象以支持链式调用。
     *
     * @param message 提示语
     * @return 当前响应
     */
    public R message(String message) {
        this.setMessage(message);
        return this;
    }

    /**
     * 设置业务数据，统一放在 {@code data.result} 下。
     *
     * @param result 任意可被 Jackson 序列化的业务对象；允许为 {@code null}
     * @return 当前响应
     */
    public R result(Object result) {
        this.data.put("result", result);
        return this;
    }

    /**
     * 向 data 中放入自定义键值，适用于校验字段等辅助信息。
     *
     * @param key   数据键名，不能为空
     * @param value 数据值
     * @return 当前响应
     */
    public R data(String key, Object value) {
        if (key != null && !key.isBlank()) {
            this.data.put(key, value);
        }
        return this;
    }

    /**
     * 批量合并 data 内容。
     *
     * @param map 待合并的键值对；为 {@code null} 时忽略
     * @return 当前响应
     */
    public R data(Map<String, Object> map) {
        if (map != null && !map.isEmpty()) {
            this.data.putAll(map);
        }
        return this;
    }
}
