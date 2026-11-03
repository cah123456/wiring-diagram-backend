package com.gridline.wiring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gridline.wiring.dto.DiagramCreateDTO;
import com.gridline.wiring.dto.DiagramQueryDTO;
import com.gridline.wiring.dto.DiagramSaveDTO;
import com.gridline.wiring.dto.DiagramUpdateDTO;
import com.gridline.wiring.entity.Diagram;
import com.gridline.wiring.exception.BusinessException;
import com.gridline.wiring.mapper.DiagramMapper;
import com.gridline.wiring.service.DiagramService;
import com.gridline.wiring.vo.DiagramStatsVO;
import com.gridline.wiring.vo.DiagramSummaryVO;
import com.gridline.wiring.vo.DiagramVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 接线图业务实现。
 *
 * <p>本类基于 MyBatis-Plus 的 {@link ServiceImpl}，统一处理图纸的增删改查、文档
 * 校验和缩略图约束。简化后的核心规则：</p>
 *
 * <ul>
 *   <li><b>物理删除</b>：直接删除记录，不保留回收站；</li>
 *   <li><b>最后写入生效</b>：保存不再要求 revision，也不生成版本快照；</li>
 *   <li><b>容量限制</b>：文档 JSON UTF-8 最大 8MB，缩略图最大 3MB，
 *       列表每页最多 60 条；</li>
 *   <li><b>只读事务</b>：查询方法标注 {@code readOnly = true}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagramServiceImpl extends ServiceImpl<DiagramMapper, Diagram>
        implements DiagramService {

    /** 画布文档 JSON 的 UTF-8 字节上限：8MB。 */
    private static final int MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;

    /** 缩略图 data URL 的字符长度上限：3MB。 */
    private static final int MAX_THUMBNAIL_BYTES = 3 * 1024 * 1024;

    /** 列表每页最大条数。 */
    private static final int MAX_PAGE_SIZE = 60;

    private final DiagramMapper diagramMapper;

    private final ObjectMapper objectMapper;

    /**
     * 分页查询图纸，按最后更新时间倒序。
     *
     * <p>入参来自 {@link DiagramQueryDTO}：页码从 1 开始（与参考项目和前端分页
     * 组件保持一致），每页条数防御性裁剪到 1~60。关键字匹配名称或说明。
     * 返回值直接使用 MyBatis-Plus 的 {@link IPage}，与参考项目一致。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public IPage<DiagramSummaryVO> listDiagrams(DiagramQueryDTO query) {
        DiagramQueryDTO dto = query == null ? new DiagramQueryDTO() : query;
        int pageNum = dto.getPageNum() == null ? 1 : Math.max(dto.getPageNum(), 1);
        int pageSize = dto.getPageSize() == null
                ? 20
                : Math.min(Math.max(dto.getPageSize(), 1), MAX_PAGE_SIZE);
        String keyword = dto.getKeyword() == null ? "" : dto.getKeyword().trim();

        LambdaQueryWrapper<Diagram> wrapper = new LambdaQueryWrapper<>();
        if (!keyword.isEmpty()) {
            wrapper.and(w -> w.like(Diagram::getName, keyword)
                    .or().like(Diagram::getDescription, keyword));
        }
        wrapper.orderByDesc(Diagram::getUpdatedAt).orderByDesc(Diagram::getId);

        // convert：逐条把实体转成 VO，同时保留分页元数据（total、current、pages 等）。
        return page(new Page<>(pageNum, pageSize), wrapper).convert(this::toSummaryVO);
    }

    /**
     * 查询工作台统计信息。聚合计算交给数据库，避免加载全表。
     */
    @Override
    @Transactional(readOnly = true)
    public DiagramStatsVO stats() {
        DiagramStatsVO vo = new DiagramStatsVO();
        vo.setDiagramCount(diagramMapper.selectCount(null));
        vo.setNodeCount(diagramMapper.sumNodeCount());
        vo.setEdgeCount(diagramMapper.sumEdgeCount());
        vo.setUpdatedRecently(diagramMapper.countUpdatedSince(
                LocalDateTime.now().minusDays(7)));
        return vo;
    }

    /**
     * 读取图纸详情（含完整画布文档）。
     */
    @Override
    @Transactional(readOnly = true)
    public DiagramVO getDetail(Long id) {
        return toDetailVO(requireDiagram(id));
    }

    /**
     * 新建图纸。未提供文档时生成默认 A3 横向空白画布。
     */
    @Override
    @Transactional
    public DiagramVO createDiagram(DiagramCreateDTO dto) {
        JsonNode document = dto.getDocument() == null
                ? defaultDocument(dto.getName(), dto.getVoltageLevel())
                : normalizeDocument(dto.getDocument());

        Diagram diagram = new Diagram();
        diagram.setName(dto.getName().trim());
        diagram.setVoltageLevel(trimToNull(dto.getVoltageLevel()));
        diagram.setDescription(trimToNull(dto.getDescription()));
        diagram.setDocumentJson(writeDocument(document));
        diagram.setNodeCount(countChildren(document, "nodes"));
        diagram.setEdgeCount(countChildren(document, "edges"));
        diagramMapper.insert(diagram);
        return toDetailVO(diagram);
    }

    /**
     * 修改元信息。使用 UpdateWrapper 显式写入 null，保证清空电压等级/说明时能落库。
     */
    @Override
    @Transactional
    public DiagramVO updateMeta(Long id, DiagramUpdateDTO dto) {
        requireDiagram(id);
        LambdaUpdateWrapper<Diagram> update = new LambdaUpdateWrapper<>();
        update.eq(Diagram::getId, id)
                .set(Diagram::getName, dto.getName().trim())
                .set(Diagram::getVoltageLevel, trimToNull(dto.getVoltageLevel()))
                .set(Diagram::getDescription, trimToNull(dto.getDescription()))
                .set(Diagram::getUpdatedAt, LocalDateTime.now());
        diagramMapper.update(null, update);
        return toDetailVO(requireDiagram(id));
    }

    /**
     * 覆盖保存画布文档。
     *
     * <p>缩略图语义：{@code null} 保持原图不变；空字符串清除缩略图；非空 data URL
     * 替换缩略图。非空值必须是 {@code data:image/} 开头且不超过 3MB。</p>
     */
    @Override
    @Transactional
    public DiagramVO saveDocument(Long id, DiagramSaveDTO dto) {
        requireDiagram(id);

        JsonNode document = normalizeDocument(dto.getDocument());
        String json = writeDocument(document);
        String thumbnail = dto.getThumbnail();

        LambdaUpdateWrapper<Diagram> update = new LambdaUpdateWrapper<>();
        update.eq(Diagram::getId, id)
                .set(Diagram::getDocumentJson, json)
                .set(Diagram::getNodeCount, countChildren(document, "nodes"))
                .set(Diagram::getEdgeCount, countChildren(document, "edges"))
                .set(Diagram::getUpdatedAt, LocalDateTime.now());

        if (thumbnail != null) {
            if (thumbnail.isBlank()) {
                update.set(Diagram::getThumbnail, null);
            } else {
                if (thumbnail.length() > MAX_THUMBNAIL_BYTES) {
                    throw BusinessException.badRequest("缩略图数据过大");
                }
                if (!thumbnail.startsWith("data:image/")) {
                    throw BusinessException.badRequest("缩略图格式无效");
                }
                update.set(Diagram::getThumbnail, thumbnail);
            }
        }
        diagramMapper.update(null, update);
        return toDetailVO(requireDiagram(id));
    }

    /**
     * 复制图纸。深拷贝画布 JSON 和缩略图；副本使用新主键，与原图完全独立。
     */
    @Override
    @Transactional
    public DiagramVO duplicate(Long id) {
        Diagram source = requireDiagram(id);
        Diagram copy = new Diagram();
        copy.setName(source.getName() + " · 副本");
        copy.setVoltageLevel(source.getVoltageLevel());
        copy.setDescription(source.getDescription());
        copy.setDocumentJson(source.getDocumentJson());
        copy.setThumbnail(source.getThumbnail());
        copy.setNodeCount(source.getNodeCount());
        copy.setEdgeCount(source.getEdgeCount());
        diagramMapper.insert(copy);
        return toDetailVO(copy);
    }

    /**
     * 物理删除图纸，删除后无法恢复。
     */
    @Override
    @Transactional
    public void deleteDiagram(Long id) {
        requireDiagram(id);
        diagramMapper.deleteById(id);
    }

    /**
     * 按主键读取，不存在时抛出 404。
     */
    private Diagram requireDiagram(Long id) {
        Diagram diagram = id == null ? null : diagramMapper.selectById(id);
        if (diagram == null) {
            throw BusinessException.notFound("图纸不存在或已被删除");
        }
        return diagram;
    }

    /**
     * 校验并规范化画布文档：必须是 JSON 对象、≤ 8MB、包含 nodes/edges 数组。
     */
    private JsonNode normalizeDocument(JsonNode document) {
        if (document == null || !document.isObject()) {
            throw BusinessException.badRequest("图纸内容格式错误");
        }
        String json = writeDocument(document);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            throw BusinessException.badRequest("图纸内容超过 8MB 限制");
        }
        if (!document.path("nodes").isArray() || !document.path("edges").isArray()) {
            throw BusinessException.badRequest("图纸内容缺少 nodes 或 edges 数组");
        }
        return document;
    }

    /**
     * 生成默认空白画布：A3 横向（1600×1131）、20px 网格、显示网格和标题栏。
     *
     * <p>字段与前端 {@code blankDocument()} 保持一致，保证“由接口直接创建”和
     * “由页面创建”得到的文档结构相同。电压等级同时写入图签 meta，避免详情里
     * 有电压等级、标题栏却为空的割裂情况。</p>
     *
     * @param title        图纸标题，写入图签
     * @param voltageLevel 电压等级，写入图签；允许为空
     */
    private JsonNode defaultDocument(String title, String voltageLevel) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("schemaVersion", 1);

        ObjectNode paper = doc.putObject("paper");
        paper.put("size", "A3");
        paper.put("orientation", "landscape");
        paper.put("width", 1600);
        paper.put("height", 1131);

        ObjectNode settings = doc.putObject("settings");
        settings.put("gridSize", 20);
        settings.put("snapToGrid", true);
        settings.put("showGrid", true);
        settings.put("showTitleBlock", true);
        settings.put("showLabels", false);

        ObjectNode meta = doc.putObject("meta");
        meta.put("title", title == null ? "未命名接线图" : title.trim());
        meta.put("drawingNo", "");
        meta.put("project", "");
        meta.put("voltageLevel", voltageLevel == null ? "" : voltageLevel.trim());
        meta.put("designer", "");
        meta.put("checker", "");
        meta.put("date", LocalDate.now().toString());
        meta.put("company", "");
        meta.put("scale", "NTS");

        doc.putArray("nodes");
        doc.putArray("edges");
        return doc;
    }

    /**
     * 将 JSON 节点序列化为字符串。
     */
    private String writeDocument(JsonNode document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw BusinessException.badRequest("图纸内容无法序列化");
        }
    }

    /**
     * 将数据库中的 JSON 字符串解析为树。解析失败说明数据损坏，返回 500。
     */
    private JsonNode readDocument(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw BusinessException.systemError("图纸数据损坏");
        }
    }

    /**
     * 统计文档中某个数组字段的元素数量。
     */
    private int countChildren(JsonNode document, String field) {
        JsonNode array = document.path(field);
        return array.isArray() ? array.size() : 0;
    }

    /**
     * 去除首尾空白，空字符串转为 null，并防御性截断到 500 字符。
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    /**
     * 实体转列表 VO。
     *
     * <p>使用 {@link BeanUtils#copyProperties} 复制同名属性，与参考项目的转换方式
     * 一致；计数列可能为 null，这里统一补 0，避免前端出现 null。</p>
     */
    private DiagramSummaryVO toSummaryVO(Diagram d) {
        DiagramSummaryVO vo = new DiagramSummaryVO();
        BeanUtils.copyProperties(d, vo);
        vo.setNodeCount(d.getNodeCount() == null ? 0 : d.getNodeCount());
        vo.setEdgeCount(d.getEdgeCount() == null ? 0 : d.getEdgeCount());
        return vo;
    }

    /**
     * 实体转详情 VO，额外解析存储的 JSON 文档。
     */
    private DiagramVO toDetailVO(Diagram d) {
        DiagramVO vo = new DiagramVO();
        BeanUtils.copyProperties(d, vo);
        vo.setNodeCount(d.getNodeCount() == null ? 0 : d.getNodeCount());
        vo.setEdgeCount(d.getEdgeCount() == null ? 0 : d.getEdgeCount());
        vo.setDocument(readDocument(d.getDocumentJson()));
        return vo;
    }
}
