package com.gridline.wiring.diagram.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 保存画布请求体。
 *
 * <p>对应接口 {@code PUT /api/diagrams/{id}}。保存采用“整份文档覆盖 + 乐观锁”：</p>
 * <ul>
 *   <li>{@code revision} 必须是客户端当前持有的修订号，服务端与数据库比对，
 *       不一致则返回 HTTP 409，提示用户重新载入，避免覆盖他人的修改；</li>
 *   <li>{@code document} 会整体替换当前画布，并写入一条新的历史版本；</li>
 *   <li>{@code thumbnail} 为可选的缩略图 data URL，最大 3MB。传空字符串表示清除
 *       已有缩略图，传 null 表示保持现有缩略图不变。</li>
 * </ul>
 *
 * @param revision   客户端已知的修订号，必填且 ≥ 1
 * @param document   完整画布文档，必填；必须包含 nodes、edges 数组，序列化后 ≤ 8MB
 * @param thumbnail  缩略图 data URL，可选
 * @param changeNote 变更说明，可选；为空时服务端自动生成“保存于 yyyy-MM-dd HH:mm”
 */
public record SaveDiagramRequest(
        @NotNull(message = "缺少图纸版本号")
        @Min(value = 1, message = "图纸版本号无效")
        Integer revision,

        @NotNull(message = "图纸内容不能为空")
        JsonNode document,

        String thumbnail,

        @Size(max = 255, message = "变更说明不能超过255个字符")
        String changeNote
) {
}
