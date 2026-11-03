package com.gridline.wiring.vo;

import lombok.Data;

/**
 * 工作台统计视图对象。
 *
 * <p>节点数、导线数来自主表的冗余计数列，不需要解析画布 JSON，因此统计接口
 * 开销很小。</p>
 */
@Data
public class DiagramStatsVO {

    /** 图纸总数。 */
    private long diagramCount;

    /** 所有图纸的节点总数。 */
    private long nodeCount;

    /** 所有图纸的导线总数。 */
    private long edgeCount;

    /** 最近 7 天更新过的图纸数。 */
    private long updatedRecently;
}
