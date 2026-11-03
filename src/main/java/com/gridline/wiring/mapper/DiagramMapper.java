package com.gridline.wiring.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gridline.wiring.entity.Diagram;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 接线图数据访问层。
 *
 * <p>继承 {@link BaseMapper} 后自动获得单表增删改查与分页能力；这里只补充统计接口
 * 需要的聚合 SQL，避免把全表数据加载到内存再计算。</p>
 *
 * <p>使用 {@code @Mapper} 而不是在启动类写 {@code @MapperScan}，这样 WebMvcTest
 * 只加载 Web 层时不会创建 Mapper Bean，测试无需数据库连接。</p>
 */
@Mapper
public interface DiagramMapper extends BaseMapper<Diagram> {

    /**
     * 统计所有图纸的节点总数。
     *
     * @return 节点总数；没有数据时 COALESCE 保证返回 0
     */
    @Select("SELECT COALESCE(SUM(node_count), 0) FROM wiring_diagram")
    long sumNodeCount();

    /**
     * 统计所有图纸的导线总数。
     *
     * @return 导线总数；没有数据时返回 0
     */
    @Select("SELECT COALESCE(SUM(edge_count), 0) FROM wiring_diagram")
    long sumEdgeCount();

    /**
     * 统计指定时间之后更新过的图纸数量。
     *
     * @param since 起始时间（含）
     * @return 图纸数量
     */
    @Select("SELECT COUNT(*) FROM wiring_diagram WHERE updated_at >= #{since}")
    long countUpdatedSince(@Param("since") LocalDateTime since);
}
