package com.gridline.wiring.diagram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 接线图历史版本实体，对应表 {@code wiring_diagram_version}。
 *
 * <p>每次创建、保存、复制、恢复历史版本都会生成一条快照。快照保存完整的文档 JSON，
 * 而不是增量 diff，原因是画布文档体积可控（上限 8MB），完整快照读取简单、恢复可靠，
 * 不依赖中间版本是否完整。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>{@code (diagram_id, revision)} 唯一，保证同一修订号不会重复写入；</li>
 *   <li>外键 {@code diagram_id} 级联删除，但当前业务只做软删除，因此正常流程不会
 *       触发级联；仅物理清理图纸时才需要数据库级联兜底；</li>
 *   <li>版本列表接口只返回最近 50 条，避免一次读取过多快照；更早的数据仍保留在库中，
 *       需要时可通过版本主键直接查询。</li>
 * </ul>
 */
@Entity
@Table(name = "wiring_diagram_version")
public class DiagramVersion {

    /** 主键，数据库自增。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属图纸。使用 LAZY 避免查询版本列表时把整张图纸（含大字段）一并加载；
     * 本项目只通过 diagramId 查询，不会在序列化时触发懒加载。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private Diagram diagram;

    /** 对应的图纸修订号；同一图纸内唯一，与主表保存时的 revision 一致。 */
    @Column(nullable = false)
    private int revision;

    /** 该版本的完整画布 JSON，与主表 document_json 同结构。 */
    @Lob
    @Column(name = "document_json", nullable = false, columnDefinition = "LONGTEXT")
    private String documentJson;

    /** 变更说明，如“保存于 2026-11-03 10:20”“恢复至版本 R3”；最长 255 字符。 */
    @Column(name = "change_note", length = 255)
    private String changeNote;

    /** 快照创建时间。列表与详情均按该字段展示。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    public void setDiagram(Diagram diagram) {
        this.diagram = diagram;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
