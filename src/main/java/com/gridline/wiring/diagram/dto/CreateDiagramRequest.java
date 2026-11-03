package com.gridline.wiring.diagram.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建图纸请求体。
 *
 * <p>对应接口 {@code POST /api/diagrams}。前端可以不传 {@code document}，此时服务端
 * 会生成一份默认 A3 横向空白图纸（含纸张、网格、标题栏设置和空的 nodes/edges 数组）。
 * 若传了 {@code document}，则必须是对象且包含 nodes、edges 数组，否则返回 400。</p>
 *
 * @param name         图纸名称，必填，最长 120 字符
 * @param voltageLevel 电压等级，可选，最长 32 字符
 * @param description  图纸说明，可选，最长 500 字符
 * @param document     初始画布文档；为 null 时使用默认空白文档
 */
public record CreateDiagramRequest(
        @NotBlank(message = "图纸名称不能为空")
        @Size(max = 120, message = "图纸名称不能超过120个字符")
        String name,

        @Size(max = 32, message = "电压等级不能超过32个字符")
        String voltageLevel,

        @Size(max = 500, message = "描述不能超过500个字符")
        String description,

        JsonNode document
) {
}
