package com.gridline.wiring.dto;

import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>所有需要分页的查询 DTO 都继承本类，避免每个查询对象重复声明分页字段。
 * 页码从 <b>1</b> 开始，与前端分页组件（Element Plus）保持一致；
 * 每页条数默认 20，服务层会把上限裁剪到 60。</p>
 */
@Data
public class PageDTO {

    /** 当前页码，从 1 开始。 */
    private Integer pageNum = 1;

    /** 每页条数，默认 20。 */
    private Integer pageSize = 20;
}
