package com.gridline.wiring.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.gridline.wiring.dto.DiagramCreateDTO;
import com.gridline.wiring.dto.DiagramQueryDTO;
import com.gridline.wiring.dto.DiagramSaveDTO;
import com.gridline.wiring.dto.DiagramUpdateDTO;
import com.gridline.wiring.entity.Diagram;
import com.gridline.wiring.vo.DiagramStatsVO;
import com.gridline.wiring.vo.DiagramSummaryVO;
import com.gridline.wiring.vo.DiagramVO;

/**
 * 接线图业务接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link IService} 获得标准 CRUD；这里额外声明图纸领域方法。
 * 方法入参使用 {@code dto} 包下的传输对象，出参使用 {@code vo} 包下的视图对象，
 * Controller 只依赖本接口，不直接接触 Mapper 和实体。</p>
 */
public interface DiagramService extends IService<Diagram> {

    /**
     * 分页查询图纸。
     *
     * @param query 关键字与分页参数（pageNum 从 1 开始，pageSize 限制在 1~60）
     * @return MyBatis-Plus 分页对象，records 为列表项 VO，不含完整画布文档
     */
    IPage<DiagramSummaryVO> listDiagrams(DiagramQueryDTO query);

    /**
     * 查询工作台统计信息。
     *
     * @return 图纸数、节点数、导线数、最近 7 天更新数
     */
    DiagramStatsVO stats();

    /**
     * 查询图纸详情（含完整画布文档）。
     *
     * @param id 图纸主键
     * @return 图纸详情 VO
     */
    DiagramVO getDetail(Long id);

    /**
     * 新建图纸。
     *
     * @param dto 创建参数
     * @return 新建图纸详情
     */
    DiagramVO createDiagram(DiagramCreateDTO dto);

    /**
     * 修改名称、电压等级和说明。
     *
     * @param id  图纸主键
     * @param dto 修改参数
     * @return 更新后的图纸详情
     */
    DiagramVO updateMeta(Long id, DiagramUpdateDTO dto);

    /**
     * 覆盖保存画布文档与可选缩略图。
     *
     * @param id  图纸主键
     * @param dto 完整文档和缩略图
     * @return 保存后的图纸详情
     */
    DiagramVO saveDocument(Long id, DiagramSaveDTO dto);

    /**
     * 复制图纸，生成新编号并把名称追加“ · 副本”。
     *
     * @param id 源图纸主键
     * @return 副本详情
     */
    DiagramVO duplicate(Long id);

    /**
     * 物理删除图纸。
     *
     * @param id 图纸主键
     */
    void deleteDiagram(Long id);
}
