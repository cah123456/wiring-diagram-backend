package com.gridline.wiring.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 API 响应包装类。
 *
 * <p>后端所有接口（成功、失败、校验异常、系统异常）都以本类型返回，前端只需要按同一套
 * 结构解析，避免每个接口各自约定返回格式。序列化后的 JSON 结构如下：</p>
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
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@link #code}：业务状态码，见 {@link ResultCode}。成功固定为 20000，可预期的业务失败
 *       为 20001，系统异常为 20005。HTTP 状态码仍然保留真实语义（400/404/409/500 等），
 *       便于网关、日志和浏览器调试。</li>
 *   <li>{@link #message}：面向用户的提示语，前端可直接展示；异常响应中由
 *       {@link GlobalExceptionHandler} 填充。</li>
 *   <li>{@link #success}：布尔快捷判断，等价于 {@code code == 20000}，前端无需硬编码状态码。</li>
 *   <li>{@link #data}：统一的数据容器。业务对象约定放在 {@code result} 键下；参数校验失败时
 *       额外放入 {@code fields}，其值为“字段名 -> 错误提示”的映射。使用
 *       {@link LinkedHashMap} 保证输出顺序稳定，便于测试和日志比对。</li>
 * </ul>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * // 成功并携带数据
 * return R.ok().result(diagramService.get(id));
 *
 * // 成功但不携带数据
 * return R.ok().message("图纸已删除");
 *
 * // 失败
 * return R.error().message("图纸不存在");
 * }</pre>
 *
 * <p>本类只用于服务端向前端输出，不参与请求反序列化，因此构造器保持私有，统一通过静态
 * 工厂方法创建，避免出现“success 为 true 但 code 为错误码”之类的不一致状态。</p>
 */
public class R {

    /** 业务成功状态码，与 {@link ResultCode#SUCCESS} 保持一致。 */
    private Integer code;

    /** 面向用户的提示信息。 */
    private String message;

    /** 是否成功；成功响应该字段恒为 {@code true}。 */
    private Boolean success;

    /** 业务数据容器；无数据时保持为空 Map，保证响应结构稳定。 */
    private Map<String, Object> data = new LinkedHashMap<>();

    /**
     * 私有构造器：调用方必须通过 {@link #ok()} 或 {@link #error()} 创建实例，
     * 确保 code、message、success 三者初始状态一致。
     */
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
     * <p>适用于“创建成功”“图纸已删除”等需要更具体反馈的场景；业务状态码仍然是
     * {@link ResultCode#SUCCESS}。</p>
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
     * 设置业务状态码及对应提示语。
     *
     * <p>注意：该方法只修改 code/message，不会改变 success。通常只用于在
     * {@link #error()} 基础上切换到更具体的错误码；成功响应请使用 {@link #ok()}。</p>
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
     * 设置业务数据（推荐用法）。
     *
     * <p>所有接口的 payload 统一放在 {@code data.result} 下，前端通过
     * {@code response.data.data.result} 取值，避免不同接口使用不同键名。</p>
     *
     * @param result 任意可被 Jackson 序列化的业务对象；允许为 {@code null}
     * @return 当前响应
     */
    public R result(Object result) {
        this.data.put("result", result);
        return this;
    }

    /**
     * 向 data 中放入自定义键值，适用于分页、校验字段等辅助信息。
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

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : data;
    }
}
