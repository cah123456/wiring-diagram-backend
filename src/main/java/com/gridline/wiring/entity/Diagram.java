package com.gridline.wiring.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接线图实体，对应表 {@code wiring_diagram}。
 *
 * <p>一条记录代表一张可打开的接线图。画布内容（节点、连线、纸张设置、标题栏等）
 * 以 JSON 形式整体存放在 {@link #documentJson} 中，而不是拆成多张关系表。画布结构
 * 与前端模型强耦合、迭代频繁，整存整取可以避免频繁改表；代价是数据库无法直接按
 * 节点/连线查询，因此额外冗余 {@link #nodeCount}、{@link #edgeCount} 供统计使用。</p>
 *
 * <p>简化后的生命周期：没有版本快照、没有软删除、没有乐观锁。保存即覆盖当前文档，
 * 删除即物理删除；多窗口同时保存时以最后一次写入为准。</p>
 */
@Data
@TableName("wiring_diagram")
public class Diagram {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图纸名称，必填，最长 120 字符；列表页关键字搜索字段之一。 */
    private String name;

    /** 电压等级，如 10kV、0.4kV；可为空。 */
    private String voltageLevel;

    /** 图纸说明，可为空，最长 500 字符。 */
    private String description;

    /** 画布文档 JSON（含 schemaVersion、paper、settings、meta、nodes、edges）。 */
    private String documentJson;

    /** 列表页缩略图，保存为 {@code data:image/...;base64,...} 字符串。 */
    private String thumbnail;

    /** 文档中的节点数量，保存时从 JSON 的 nodes 数组重新统计。 */
    private Integer nodeCount;

    /** 文档中的导线数量，保存时从 JSON 的 edges 数组重新统计。 */
    private Integer edgeCount;

    /** 创建时间，插入时由 MyMetaObjectHandler 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间，插入和更新时自动填充；列表默认按该字段倒序。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
