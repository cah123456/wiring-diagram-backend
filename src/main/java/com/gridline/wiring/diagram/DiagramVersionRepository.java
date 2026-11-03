package com.gridline.wiring.diagram;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 接线图版本快照数据访问接口。
 *
 * <p>版本记录只追加、不修改：每次保存生成一条新快照，恢复历史版本也会生成一条新的
 * 快照，而不是覆盖旧记录。因此本接口没有 update/delete 业务方法，仅保留查询与新增。</p>
 */
public interface DiagramVersionRepository extends JpaRepository<DiagramVersion, Long> {

    /**
     * 查询指定图纸最近 50 条版本，按修订号倒序。
     *
     * <p>Spring Data 会把方法名拆解为：
     * {@code findTop50 By DiagramId OrderBy Revision Desc}。只取 50 条可以避免历史
     * 很长的图纸一次返回过多数据；完整历史仍保留在数据库中。</p>
     *
     * @param diagramId 图纸主键
     * @return 版本列表，最新版本在前
     */
    List<DiagramVersion> findTop50ByDiagramIdOrderByRevisionDesc(Long diagramId);

    /**
     * 按版本主键和图纸主键查询，避免通过猜测 versionId 读取其他图纸的历史版本。
     *
     * @param id        版本主键
     * @param diagramId 所属图纸主键
     * @return 匹配的版本，不存在时为空
     */
    Optional<DiagramVersion> findByIdAndDiagramId(Long id, Long diagramId);
}
