package com.gridline.wiring.dto;

import lombok.Data;

/**
 * 图纸分页查询参数，对应 {@code GET /api/diagrams}。
 *
 * <p>分页字段来自父类 {@link PageDTO}（{@code pageNum}、{@code pageSize}），
 * 本类只补充业务筛选条件。GET 请求由 Spring 直接按同名的查询参数绑定。</p>
 */
@Data
public class DiagramQueryDTO extends PageDTO {

    /** 模糊搜索关键字，同时匹配名称、编号和说明；为空表示不过滤。 */
    private String keyword;
}
