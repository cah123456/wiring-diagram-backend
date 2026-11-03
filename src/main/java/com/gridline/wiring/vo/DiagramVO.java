package com.gridline.wiring.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图纸详情视图对象，在列表字段基础上增加完整画布文档。
 *
 * <p>创建、查询详情、保存、复制成功后都返回该对象。简化后不包含 revision，
 * 前端不需要做版本冲突处理。</p>
 */
@Data
public class DiagramVO {

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

    /** 完整画布文档：schemaVersion、paper、settings、meta、nodes、edges。 */
    private JsonNode document;
}
