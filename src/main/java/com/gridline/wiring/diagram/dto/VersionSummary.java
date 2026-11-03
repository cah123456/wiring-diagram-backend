package com.gridline.wiring.diagram.dto;

import java.time.LocalDateTime;

/**
 * 历史版本摘要，不含完整文档。
 *
 * <p>用于版本时间线列表。用户选择某个版本后，再调用版本详情接口读取 document。</p>
 *
 * @param id         版本主键
 * @param revision   对应修订号
 * @param changeNote 变更说明，可为 null
 * @param createdAt  快照创建时间
 */
public record VersionSummary(
        Long id,
        int revision,
        String changeNote,
        LocalDateTime createdAt
) {
}
