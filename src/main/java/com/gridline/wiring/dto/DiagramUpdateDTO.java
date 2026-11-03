package com.gridline.wiring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改图纸元信息的传输参数，对应 {@code PATCH /api/diagrams/{id}}。
 *
 * <p>只允许修改名称、电压等级和说明，不触碰画布文档与缩略图。</p>
 */
@Data
public class DiagramUpdateDTO {

    /** 图纸名称，必填，最长 120 字符。 */
    @NotBlank(message = "图纸名称不能为空")
    @Size(max = 120, message = "图纸名称不能超过120个字符")
    private String name;

    /** 电压等级，可选，最长 32 字符；传空字符串表示清空。 */
    @Size(max = 32, message = "电压等级不能超过32个字符")
    private String voltageLevel;

    /** 图纸说明，可选，最长 500 字符；传空字符串表示清空。 */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}
