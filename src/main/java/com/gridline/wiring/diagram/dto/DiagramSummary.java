package com.gridline.wiring.diagram.dto;

import java.time.LocalDateTime;

/**
 * 图纸列表项。
 *
 * <p>用于工作台/列表页，包含卡片展示所需字段和缩略图，但不含完整画布文档，
 * 避免列表接口返回大量 JSON。打开某张图纸时再调用详情接口获取 document。</p>
 *
 * @param id           主键
 * @param name         名称
 * @param code         全局唯一编号
 * @param voltageLevel 电压等级，可为 null
 * @param description  说明，可为 null
 * @param nodeCount    节点数量
 * @param edgeCount    连线数量
 * @param revision     当前修订号
 * @param thumbnail    缩略图 data URL，可为 null
 * @param createdAt    创建时间
 * @param updatedAt    最后更新时间
 */
public record DiagramSummary(
        Long id,
        String name,
        String code,
        String voltageLevel,
        String description,
        int nodeCount,
        int edgeCount,
        int revision,
        String thumbnail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
