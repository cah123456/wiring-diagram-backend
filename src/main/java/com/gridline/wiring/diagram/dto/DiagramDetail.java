package com.gridline.wiring.diagram.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 图纸详情，包含完整画布文档。
 *
 * <p>打开编辑器、创建、保存、复制、恢复版本后都会返回该结构。{@code revision}
 * 是下一次保存必须回传的乐观锁版本号，前端应保存在编辑器状态中。</p>
 *
 * @param id           主键
 * @param name         名称
 * @param code         全局唯一编号
 * @param voltageLevel 电压等级，可为 null
 * @param description  说明，可为 null
 * @param revision     当前修订号
 * @param document     完整画布文档（schemaVersion、paper、settings、meta、nodes、edges）
 * @param thumbnail    缩略图 data URL，可为 null
 * @param createdAt    创建时间
 * @param updatedAt    最后更新时间
 */
public record DiagramDetail(
        Long id,
        String name,
        String code,
        String voltageLevel,
        String description,
        int revision,
        JsonNode document,
        String thumbnail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
