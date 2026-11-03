package com.gridline.wiring.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存画布文档的传输参数，对应 {@code PUT /api/diagrams/{id}}。
 *
 * <p>保存采用“整份文档覆盖”，不再携带 revision，也不生成历史版本。多窗口同时
 * 保存时以最后一次写入为准。</p>
 *
 * <ul>
 *   <li>{@code document} 会整体替换当前画布，必须包含 nodes、edges 数组，
 *       序列化后不超过 8MB；</li>
 *   <li>{@code thumbnail} 为可选的缩略图 data URL，最大 3MB。传空字符串表示清除
 *       已有缩略图，传 null 表示保持现有缩略图不变。</li>
 * </ul>
 */
@Data
public class DiagramSaveDTO {

    /** 完整画布文档，必填。 */
    @NotNull(message = "图纸内容不能为空")
    private JsonNode document;

    /** 缩略图 data URL，可选。 */
    private String thumbnail;
}
