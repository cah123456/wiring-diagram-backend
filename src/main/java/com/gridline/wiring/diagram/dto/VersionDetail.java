package com.gridline.wiring.diagram.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 历史版本详情，包含该版本的完整画布文档。
 *
 * <p>前端可用于只读预览或对比；恢复操作由专门的恢复接口完成，恢复后会产生新的
 * 修订号和快照，而不是回退修订号。</p>
 *
 * @param id         版本主键
 * @param revision   对应修订号
 * @param changeNote 变更说明，可为 null
 * @param createdAt  快照创建时间
 * @param document   该版本的完整画布文档
 */
public record VersionDetail(
        Long id,
        int revision,
        String changeNote,
        LocalDateTime createdAt,
        JsonNode document
) {
}
