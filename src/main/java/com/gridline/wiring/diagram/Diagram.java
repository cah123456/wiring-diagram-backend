package com.gridline.wiring.diagram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 接线图主表实体，对应表 {@code wiring_diagram}。
 *
 * <p>一条记录代表一张可打开的接线图。画布内容（节点、连线、纸张设置、标题栏等）
 * 以 JSON 形式整体存放在 {@link #documentJson} 中，而不是拆成多张关系表。这样做的
 * 原因是画布数据结构和前端模型强耦合、迭代频繁，整存整取可以避免频繁改表；代价是
 * 数据库无法直接按节点/连线查询，因此额外冗余了 {@link #nodeCount}、
 * {@link #edgeCount} 两个计数列，供工作台统计使用。</p>
 *
 * <p>生命周期与并发：</p>
 * <ul>
 *   <li>删除采用软删除，{@link #status} 从 {@code ACTIVE} 变为 {@code DELETED}，
 *       记录与历史版本都保留；</li>
 *   <li>{@link #revision} 是乐观锁版本号，每次保存/恢复自增；前端保存时必须回传
 *       当前版本号，服务端在悲观锁事务中比对，防止多标签页互相覆盖；</li>
 *   <li>每次保存都会向 {@code wiring_diagram_version} 写入同修订号的快照。</li>
 * </ul>
 */
@Entity
@Table(name = "wiring_diagram")
public class Diagram {

    /** 主键，数据库自增。使用 Long 而非 int，为长期增长留余量。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 图纸名称，必填，最长 120 字符；列表页的关键字搜索字段之一。 */
    @Column(nullable = false, length = 120)
    private String name;

    /** 图纸编号，形如 {@code WD-20261103-AB3K}，全局唯一，由服务端生成。 */
    @Column(nullable = false, length = 64, unique = true)
    private String code;

    /** 电压等级，如 10kV、0.4kV；可为空，最长 32 字符。 */
    @Column(name = "voltage_level", length = 32)
    private String voltageLevel;

    /** 图纸说明，可为空，最长 500 字符。 */
    @Column(length = 500)
    private String description;

    /**
     * 画布文档 JSON（含 schemaVersion、paper、settings、meta、nodes、edges）。
     * 序列化后的 UTF-8 字节数上限为 8MB，由服务层校验。
     */
    @Lob
    @Column(name = "document_json", nullable = false, columnDefinition = "LONGTEXT")
    private String documentJson;

    /**
     * 列表页缩略图，保存为 {@code data:image/...;base64,...} 字符串。
     * 上限 3MB；恢复历史版本时会清空，等待下一次保存重新生成。
     */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String thumbnail;

    /** 文档中的节点数量，保存时从 JSON 的 nodes 数组重新统计，用于统计页展示。 */
    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    /** 文档中的连线数量，保存时从 JSON 的 edges 数组重新统计。 */
    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    /**
     * 乐观锁修订号，从 1 开始，每次保存或恢复历史版本加 1。
     * 同一图纸的所有版本快照共享递增序列。
     */
    @Column(nullable = false)
    private int revision;

    /** 记录状态：{@code ACTIVE} 正常，{@code DELETED} 已软删除。 */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** 创建时间（应用层赋值，精确到毫秒）。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间；列表默认按该字段倒序，保存/改元信息/删除时刷新。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getVoltageLevel() {
        return voltageLevel;
    }

    public void setVoltageLevel(String voltageLevel) {
        this.voltageLevel = voltageLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
