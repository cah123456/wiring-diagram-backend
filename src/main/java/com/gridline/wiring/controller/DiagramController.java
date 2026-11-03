package com.gridline.wiring.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gridline.wiring.common.R;
import com.gridline.wiring.dto.DiagramCreateDTO;
import com.gridline.wiring.dto.DiagramQueryDTO;
import com.gridline.wiring.dto.DiagramSaveDTO;
import com.gridline.wiring.dto.DiagramUpdateDTO;
import com.gridline.wiring.service.DiagramService;
import com.gridline.wiring.vo.DiagramStatsVO;
import com.gridline.wiring.vo.DiagramSummaryVO;
import com.gridline.wiring.vo.DiagramVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接线图 REST 接口。
 *
 * <p>分层约定与参考项目一致：</p>
 * <ul>
 *   <li>接收前端参数使用 {@code dto} 包下的 {@code *DTO} 类，由 Bean Validation 校验；</li>
 *   <li>返回前端数据使用 {@code vo} 包下的 {@code *VO} 类，避免直接暴露实体；</li>
 *   <li>分页查询返回 MyBatis-Plus 的 {@link IPage}，其中 {@code records} 为 VO 列表；</li>
 *   <li>所有方法统一返回 {@link R}，业务数据放在 {@code data.result} 中。</li>
 * </ul>
 *
 * <p>HTTP 语义保持不变：创建返回 201，查询/修改/删除返回 200。错误响应由
 * {@code GlobalExceptionHandler} 统一包装，因此本类不写 try/catch。</p>
 */
@RestController
@RequestMapping("/api/diagrams")
@RequiredArgsConstructor
public class DiagramController {

    private final DiagramService diagramService;

    /**
     * 分页查询接线图。查询参数直接绑定到 {@link DiagramQueryDTO}。
     *
     * @param query 关键字与分页参数（pageNum 从 1 开始，pageSize 默认 20）
     * @return result 为 {@link IPage}，records 为 {@link DiagramSummaryVO} 列表
     */
    @GetMapping
    public R list(DiagramQueryDTO query) {
        return R.ok().result(diagramService.listDiagrams(query));
    }

    /**
     * 查询工作台统计信息。
     *
     * @return result 为 {@link DiagramStatsVO}
     */
    @GetMapping("/stats")
    public R stats() {
        return R.ok().result(diagramService.stats());
    }

    /**
     * 按主键查询图纸详情（含完整画布文档）。
     *
     * @param id 图纸主键
     * @return result 为 {@link DiagramVO}
     */
    @GetMapping("/{id}")
    public R get(@PathVariable Long id) {
        return R.ok().result(diagramService.getDetail(id));
    }

    /**
     * 新建图纸。
     *
     * @param dto 创建参数
     * @return HTTP 201，result 为新建图纸详情
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R create(@Valid @RequestBody DiagramCreateDTO dto) {
        return R.ok("图纸创建成功").result(diagramService.createDiagram(dto));
    }

    /**
     * 修改图纸名称、电压等级和说明。
     *
     * @param id  图纸主键
     * @param dto 元信息修改参数
     * @return result 为更新后的 {@link DiagramVO}
     */
    @PatchMapping("/{id}")
    public R updateMeta(@PathVariable Long id,
                        @Valid @RequestBody DiagramUpdateDTO dto) {
        return R.ok("图纸信息已更新").result(diagramService.updateMeta(id, dto));
    }

    /**
     * 保存画布文档。保存为整份覆盖，最后一次写入生效。
     *
     * @param id  图纸主键
     * @param dto 画布文档及可选缩略图
     * @return result 为保存后的 {@link DiagramVO}
     */
    @PutMapping("/{id}")
    public R save(@PathVariable Long id,
                  @Valid @RequestBody DiagramSaveDTO dto) {
        return R.ok("图纸已保存").result(diagramService.saveDocument(id, dto));
    }

    /**
     * 复制图纸，名称追加“ · 副本”，画布内容深拷贝。
     *
     * @param id 源图纸主键
     * @return HTTP 201，result 为副本详情
     */
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public R duplicate(@PathVariable Long id) {
        return R.ok("图纸已复制").result(diagramService.duplicate(id));
    }

    /**
     * 物理删除图纸，删除后不可恢复。
     *
     * @param id 图纸主键
     * @return 成功响应，不携带 result
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        diagramService.deleteDiagram(id);
        return R.ok("图纸已删除");
    }
}
