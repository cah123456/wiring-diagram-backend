package com.gridline.wiring.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建图纸的传输参数，对应 {@code POST /api/diagrams}。
 *
 * <p>DTO（Data Transfer Object）只负责接收前端传入的数据并承载字段校验注解，
 * 不参与持久化。{@code document} 可为空，服务层会生成一份默认 A3 横向空白画布；
 * 若传入则必须是包含 nodes、edges 数组的 JSON 对象。</p>
 */
@Data
public class DiagramCreateDTO {

    /** 图纸名称，必填，最长 120 字符。 */
    @NotBlank(message = "图纸名称不能为空")
    @Size(max = 120, message = "图纸名称不能超过120个字符")
    private String name;

    /** 电压等级，例如 10kV、0.4kV；可选，最长 32 字符。 */
    @Size(max = 32, message = "电压等级不能超过32个字符")
    private String voltageLevel;

    /** 图纸说明，可选，最长 500 字符。 */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /** 初始画布文档；为 null 时由服务层生成默认空白文档。 */
    private JsonNode document;
}
