package com.gridline.wiring.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图纸列表项视图对象。
 *
 * <p>VO（View Object）专门用于返回给前端，只包含图纸库卡片需要的字段，
 * 不携带完整画布文档，避免列表接口返回大量 JSON。打开编辑器时再调用详情接口
 * 获取 {@link DiagramVO#getDocument()}。</p>
 */
@Data
public class DiagramSummaryVO {

    /** 主键。 */
    private Long id;

    /** 图纸名称。 */
    private String name;

    /** 电压等级，可为 null。 */
    private String voltageLevel;

    /** 图纸说明，可为 null。 */
    private String description;

    /** 节点数量。 */
    private Integer nodeCount;

    /** 导线数量。 */
    private Integer edgeCount;

    /** 缩略图 data URL，可为 null。 */
    private String thumbnail;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
