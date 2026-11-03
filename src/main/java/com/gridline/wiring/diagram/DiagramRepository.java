package com.gridline.wiring.diagram;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 接线图主表数据访问接口。
 *
 * <p>所有面向用户的查询都显式过滤 {@code status = 'ACTIVE'}，因此软删除后的图纸
 * 不会出现在列表、详情、统计中。更新类操作使用
 * {@link #findActiveForUpdate(Long)} 加悲观写锁，保证“读取 revision → 校验 → 自增”
 * 这一串逻辑在并发下串行执行。</p>
 *
 * <p>本接口继承 {@link JpaRepository}，基础 CRUD 由 Spring Data 自动实现；下面的
 * 方法按用途分为：列表检索、详情读取、并发更新、唯一性检查、统计聚合。</p>
 */
public interface DiagramRepository extends JpaRepository<Diagram, Long> {

    /**
     * 分页搜索未删除的图纸。
     *
     * <p>关键字同时匹配名称和编号，大小写不敏感；传入空字符串表示不按关键字过滤。
     * 排序由调用方通过 {@link Pageable} 指定（默认按 updatedAt 倒序）。</p>
     *
     * <p>注意：显式 {@code :keyword = ''} 判断保证空关键字时不会生成
     * {@code like '%%'} 导致全表扫描的额外条件（查询优化器仍可命中状态索引）。</p>
     *
     * @param keyword  搜索关键字，不能为 null
     * @param pageable 分页与排序参数
     * @return 分页结果
     */
    @Query("""
            select d from Diagram d
            where d.status = 'ACTIVE'
              and (:keyword = '' or lower(d.name) like lower(concat('%', :keyword, '%'))
                   or lower(d.code) like lower(concat('%', :keyword, '%')))
            """)
    Page<Diagram> searchActive(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 按主键和状态查询图纸，用于只读详情与复制场景。
     *
     * @param id     图纸主键
     * @param status 目标状态，通常传 {@code ACTIVE}
     * @return 匹配的图纸，不存在时为空
     */
    Optional<Diagram> findByIdAndStatus(Long id, String status);

    /**
     * 查询未删除图纸并加悲观写锁（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>用于保存、修改元信息、删除、恢复等写操作。锁在事务提交时释放，保证同一图纸
     * 的并发保存按顺序执行：后到的事务看到的必定是前一个事务提交后的 revision，
     * 从而让乐观锁冲突判断准确可靠。</p>
     *
     * @param id 图纸主键
     * @return 加锁后的图纸，不存在或已删除时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Diagram d where d.id = :id and d.status = 'ACTIVE'")
    Optional<Diagram> findActiveForUpdate(@Param("id") Long id);

    /**
     * 判断图纸编号是否已存在。
     *
     * <p>用于生成随机编号时避免碰撞。数据库唯一索引是最终防线；由于
     * {@code code} 带日期和随机后缀，碰撞概率极低，生成逻辑最多重试 20 次。</p>
     *
     * @param code 待检查的编号
     * @return 存在返回 {@code true}
     */
    boolean existsByCode(String code);

    /**
     * 统计指定状态的图纸数量。
     *
     * @param status 状态，工作台统计传 {@code ACTIVE}
     * @return 记录数
     */
    long countByStatus(String status);

    /**
     * 汇总所有未删除图纸的节点数。
     *
     * <p>使用 {@code coalesce(..., 0)} 保证没有数据时返回 0 而不是 null。</p>
     *
     * @return 节点总数
     */
    @Query("select coalesce(sum(d.nodeCount), 0) from Diagram d where d.status = 'ACTIVE'")
    long sumNodeCount();

    /**
     * 汇总所有未删除图纸的连线数。
     *
     * @return 连线总数
     */
    @Query("select coalesce(sum(d.edgeCount), 0) from Diagram d where d.status = 'ACTIVE'")
    long sumEdgeCount();

    /**
     * 统计自指定时间以来更新过的未删除图纸数量。
     *
     * <p>工作台“最近 7 天更新”使用该查询。参数使用 {@link LocalDateTime}，
     * Hibernate 的 JDBC 时区配置为 {@code Asia/Shanghai}，与数据库 DATETIME 保持一致。</p>
     *
     * @param since 起始时间（含）
     * @return 图纸数量
     */
    @Query("select count(d) from Diagram d where d.status = 'ACTIVE' and d.updatedAt >= :since")
    long countUpdatedSince(@Param("since") LocalDateTime since);
}
