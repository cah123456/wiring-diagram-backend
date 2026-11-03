package com.gridline.wiring.diagram.dto;

/**
 * 工作台统计信息。
 *
 * <p>所有统计只针对未删除（{@code ACTIVE}）的图纸；节点数、连线数来自主表的冗余
 * 计数列，不需要解析画布 JSON，因此统计接口开销很小。</p>
 *
 * @param diagramCount    图纸总数
 * @param nodeCount       所有图纸的节点总数
 * @param edgeCount       所有图纸的连线总数
 * @param updatedRecently 最近 7 天更新过的图纸数
 */
public record DiagramStats(
        long diagramCount,
        long nodeCount,
        long edgeCount,
        long updatedRecently
) {
}
