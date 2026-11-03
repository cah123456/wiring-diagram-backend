package com.gridline.wiring.diagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改图纸元信息请求体。
 *
 * <p>对应接口 {@code PATCH /api/diagrams/{id}}。只修改名称、电压等级和说明，
 * 不会触碰画布文档、缩略图或修订号，因此不会产生历史版本；画布内容请使用保存接口。</p>
 *
 * @param name         图纸名称，必填，最长 120 字符
 * @param voltageLevel 电压等级，可选，最长 32 字符
 * @param description  图纸说明，可选，最长 500 字符
 */
public record UpdateDiagramRequest(
        @NotBlank(message = "图纸名称不能为空")
        @Size(max = 120, message = "图纸名称不能超过120个字符")
        String name,

        @Size(max = 32, message = "电压等级不能超过32个字符")
        String voltageLevel,

        @Size(max = 500, message = "描述不能超过500个字符")
        String description
) {
}
