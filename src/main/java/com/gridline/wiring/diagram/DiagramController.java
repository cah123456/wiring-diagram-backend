package com.gridline.wiring.diagram;

import com.gridline.wiring.common.R;
import com.gridline.wiring.diagram.dto.CreateDiagramRequest;
import com.gridline.wiring.diagram.dto.DiagramDetail;
import com.gridline.wiring.diagram.dto.DiagramStats;
import com.gridline.wiring.diagram.dto.DiagramSummary;
import com.gridline.wiring.diagram.dto.PageResponse;
import com.gridline.wiring.diagram.dto.SaveDiagramRequest;
import com.gridline.wiring.diagram.dto.UpdateDiagramRequest;
import com.gridline.wiring.diagram.dto.VersionDetail;
import com.gridline.wiring.diagram.dto.VersionSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接线图 REST 接口。
 *
 * <p>所有方法统一返回 {@link R}，业务数据放在 {@code data.result} 中：</p>
 * <ul>
 *   <li>列表接口：result 为 {@link PageResponse}；</li>
 *   <li>详情/保存/恢复接口：result 为 {@link DiagramDetail}；</li>
 *   <li>版本列表：result 为 {@link VersionSummary} 数组；</li>
 *   <li>删除、统计等无业务对象的接口：省略 result，只返回提示语。</li>
 * </ul>
 *
 * <p>HTTP 语义保持不变：创建返回 201，查询/修改/删除返回 200。错误响应由
 * {@code GlobalExceptionHandler} 统一包装，因此本类不写 try/catch。</p>
 */
@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    /** 图纸业务服务，负责数据库访问、版本快照和并发控制。 */
    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    /**
     * 分页查询未删除的接线图。
     *
     * @param keyword 名称/编号/备注的模糊搜索关键字，空字符串表示不过滤
     * @param page    页码，从 0 开始
     * @param size    每页条数，服务层会限制上限，防止一次拉取过多数据
     * @return result 为分页结果
     */
    @GetMapping
    public R list(@RequestParam(defaultValue = "") String keyword,
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "24") int size) {
        return R.ok().result(diagramService.list(keyword, page, size));
    }

    /**
     * 查询工作台统计信息（图纸数、节点数、连线数、最近更新数）。
     *
     * @return result 为 {@link DiagramStats}
     */
    @GetMapping("/stats")
    public R stats() {
        return R.ok().result(diagramService.stats());
    }

    /**
     * 按主键查询图纸详情（含完整画布文档）。
     *
     * @param id 图纸主键
     * @return result 为 {@link DiagramDetail}
     */
    @GetMapping("/{id}")
    public R get(@PathVariable Long id) {
        return R.ok().result(diagramService.get(id));
    }

    /**
     * 新建图纸。
     *
     * <p>请求体由 Bean Validation 校验；成功后返回 HTTP 201，result 为新建图纸详情。</p>
     *
     * @param request 创建参数（名称、描述等）
     * @return result 为 {@link DiagramDetail}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R create(@Valid @RequestBody CreateDiagramRequest request) {
        return R.ok("图纸创建成功").result(diagramService.create(request));
    }

    /**
     * 修改图纸元信息（名称、描述），不触碰画布文档和修订号。
     *
     * @param id      图纸主键
     * @param request 元信息修改参数
     * @return result 为更新后的 {@link DiagramDetail}
     */
    @PatchMapping("/{id}")
    public R updateMeta(@PathVariable Long id,
                        @Valid @RequestBody UpdateDiagramRequest request) {
        return R.ok("图纸信息已更新").result(diagramService.updateMeta(id, request));
    }

    /**
     * 保存画布文档。
     *
     * <p>请求体中的 revision 用于乐观锁：必须与数据库当前修订号一致，否则返回 409，
     * 避免两个标签页互相覆盖。保存成功会生成一条版本快照并使 revision 自增。</p>
     *
     * @param id      图纸主键
     * @param request 画布文档及期望修订号
     * @return result 为保存后的 {@link DiagramDetail}（含新 revision）
     */
    @PutMapping("/{id}")
    public R save(@PathVariable Long id, @Valid @RequestBody SaveDiagramRequest request) {
        return R.ok("图纸已保存").result(diagramService.save(id, request));
    }

    /**
     * 复制图纸，生成新的编号和名称，画布内容深拷贝。
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
     * 软删除图纸。
     *
     * <p>物理记录保留，状态改为 DELETED，版本快照一并保留，便于后续恢复或审计。
     * 删除成功返回 HTTP 200 和提示语，不携带 result。</p>
     *
     * @param id 图纸主键
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        diagramService.delete(id);
        return R.ok("图纸已删除");
    }

    /**
     * 查询指定图纸的最近 50 条版本快照。
     *
     * @param id 图纸主键
     * @return result 为版本摘要数组，按 revision 倒序
     */
    @GetMapping("/{id}/versions")
    public R versions(@PathVariable Long id) {
        return R.ok().result(diagramService.versions(id));
    }

    /**
     * 查询单条版本快照的完整文档。
     *
     * @param id        图纸主键
     * @param versionId 版本主键
     * @return result 为 {@link VersionDetail}
     */
    @GetMapping("/{id}/versions/{versionId}")
    public R version(@PathVariable Long id, @PathVariable Long versionId) {
        return R.ok().result(diagramService.version(id, versionId));
    }

    /**
     * 将历史版本恢复到当前图纸。
     *
     * <p>恢复不是“覆盖为旧文档”那么简单：会校验客户端 revision，生成一条新版本快照，
     * 使修订号继续前进，保证历史链完整可追溯。</p>
     *
     * @param id        图纸主键
     * @param versionId 要恢复的版本主键
     * @param revision  客户端当前持有的修订号，用于乐观锁
     * @return result 为恢复后的 {@link DiagramDetail}
     */
    @PostMapping("/{id}/versions/{versionId}/restore")
    public R restore(@PathVariable Long id, @PathVariable Long versionId,
                     @RequestParam int revision) {
        return R.ok("版本已恢复").result(diagramService.restore(id, versionId, revision));
    }
}
