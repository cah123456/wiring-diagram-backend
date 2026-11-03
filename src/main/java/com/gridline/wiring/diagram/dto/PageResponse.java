package com.gridline.wiring.diagram.dto;

import java.util.List;

/**
 * 通用分页响应。
 *
 * <p>放在统一响应 {@code R.data.result} 中返回。字段命名与前端分页组件保持一致：
 * 页码从 0 开始，{@code totalPages} 由服务端按总数和每页条数计算。</p>
 *
 * @param items      当前页数据
 * @param total      总记录数
 * @param page       当前页码（从 0 开始）
 * @param size       每页条数（已按服务端上限裁剪）
 * @param totalPages 总页数
 * @param <T>        列表元素类型
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {
}
