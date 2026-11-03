package com.gridline.wiring.diagram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gridline.wiring.common.ApiException;
import com.gridline.wiring.diagram.dto.CreateDiagramRequest;
import com.gridline.wiring.diagram.dto.DiagramDetail;
import com.gridline.wiring.diagram.dto.DiagramStats;
import com.gridline.wiring.diagram.dto.DiagramSummary;
import com.gridline.wiring.diagram.dto.PageResponse;
import com.gridline.wiring.diagram.dto.SaveDiagramRequest;
import com.gridline.wiring.diagram.dto.UpdateDiagramRequest;
import com.gridline.wiring.diagram.dto.VersionDetail;
import com.gridline.wiring.diagram.dto.VersionSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 接线图业务服务。
 *
 * <p>本类集中实现图纸的增删改查、版本快照和并发控制，Controller 只做协议转换，
 * Repository 只做数据访问。核心规则如下：</p>
 *
 * <ul>
 *   <li><b>软删除</b>：删除只把 status 改为 DELETED，历史版本保留，查询统一过滤
 *       ACTIVE；</li>
 *   <li><b>乐观锁 + 悲观锁</b>：客户端提交 revision，服务端在写锁事务中比对；
 *       版本号不一致返回 409，防止多窗口覆盖；</li>
 *   <li><b>版本快照</b>：创建、保存、复制、恢复都会写入
 *       {@code wiring_diagram_version}，快照保存完整文档，恢复时生成新版本而不是
 *       回退修订号；</li>
 *   <li><b>容量限制</b>：文档 JSON UTF-8 最大 8MB，缩略图最大 3MB，列表每页
 *       最多 60 条，版本列表最多 50 条；</li>
 *   <li><b>只读事务</b>：查询方法标注 {@code readOnly = true}，便于数据库优化并
 *       防止误写。</li>
 * </ul>
 */
@Service
public class DiagramService {

    /** 画布文档 JSON 的 UTF-8 字节上限：8MB。超限直接拒绝，避免数据库大字段失控。 */
    private static final int MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;

    /**
     * 缩略图 data URL 的字符长度上限：3MB。
     *
     * <p>base64 字符数约为原始字节的 4/3，3MB 字符长度对应约 2.25MB 图片数据；
     * 缩略图只是列表预览，不需要更高精度。</p>
     */
    private static final int MAX_THUMBNAIL_BYTES = 3 * 1024 * 1024;

    /** 安全随机数生成器，用于生成图纸编号后缀，避免使用可预测的 Random。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 编号后缀字符集：去掉容易混淆的 I、O、0、1，保留 32 个字符。
     * 4 位后缀约有 104 万种组合，配合日期前缀足以避免碰撞。
     */
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** 编号中的日期格式，例如 20261103。 */
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 自动生成的变更说明时间格式。 */
    private static final DateTimeFormatter SAVE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 图纸主表数据访问。 */
    private final DiagramRepository diagramRepository;

    /** 版本快照数据访问。 */
    private final DiagramVersionRepository versionRepository;

    /** Jackson 对象映射器，用于 JsonNode 与存储字符串互转。 */
    private final ObjectMapper objectMapper;

    public DiagramService(DiagramRepository diagramRepository,
                          DiagramVersionRepository versionRepository,
                          ObjectMapper objectMapper) {
        this.diagramRepository = diagramRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询未删除图纸。
     *
     * <p>对入参做防御性裁剪：页码不能为负，每页条数限制在 1~60。关键字会去除首尾
     * 空格，null 视为空字符串。结果按最后更新时间倒序，最近编辑的图纸排在最前。</p>
     *
     * @param keyword 名称/编号关键字，可为 null
     * @param page    页码，从 0 开始
     * @param size    每页条数
     * @return 分页结果，列表项不包含完整画布文档
     */
    @Transactional(readOnly = true)
    public PageResponse<DiagramSummary> list(String keyword, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 60);
        String query = keyword == null ? "" : keyword.trim();
        Page<Diagram> result = diagramRepository.searchActive(query,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<DiagramSummary> items = result.getContent().stream().map(this::toSummary).toList();
        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(),
                result.getSize(), result.getTotalPages());
    }

    /**
     * 查询工作台统计信息。
     *
     * @return 图纸数、节点数、连线数、最近 7 天更新数
     */
    @Transactional(readOnly = true)
    public DiagramStats stats() {
        return new DiagramStats(
                diagramRepository.countByStatus("ACTIVE"),
                diagramRepository.sumNodeCount(),
                diagramRepository.sumEdgeCount(),
                diagramRepository.countUpdatedSince(LocalDateTime.now().minusDays(7))
        );
    }

    /**
     * 读取图纸详情（含完整画布文档）。
     *
     * @param id 图纸主键
     * @return 图纸详情
     * @throws ApiException 图纸不存在或已删除时抛出 404
     */
    @Transactional(readOnly = true)
    public DiagramDetail get(Long id) {
        Diagram diagram = diagramRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));
        return toDetail(diagram);
    }

    /**
     * 新建图纸。
     *
     * <p>未提供文档时生成默认 A3 横向空白画布；提供文档时先校验结构和大小。编号由
     * 服务端生成，修订号从 1 开始，并立即写入一条“创建图纸”版本快照。</p>
     *
     * @param request 创建参数
     * @return 新建图纸详情
     */
    @Transactional
    public DiagramDetail create(CreateDiagramRequest request) {
        JsonNode document = request.document() == null || request.document().isNull()
                ? defaultDocument(request.name())
                : normalizeDocument(request.document());
        LocalDateTime now = LocalDateTime.now();

        Diagram diagram = new Diagram();
        diagram.setName(request.name().trim());
        diagram.setCode(nextCode());
        diagram.setVoltageLevel(trimToNull(request.voltageLevel()));
        diagram.setDescription(trimToNull(request.description()));
        diagram.setDocumentJson(writeDocument(document));
        // 计数列由文档实时推导，避免前端传入不一致的统计值。
        diagram.setNodeCount(countChildren(document, "nodes"));
        diagram.setEdgeCount(countChildren(document, "edges"));
        diagram.setRevision(1);
        diagram.setStatus("ACTIVE");
        diagram.setCreatedAt(now);
        diagram.setUpdatedAt(now);
        diagramRepository.save(diagram);

        addVersion(diagram, "创建图纸");
        return toDetail(diagram);
    }

    /**
     * 修改图纸元信息。
     *
     * <p>不改变画布文档和 revision，因此不产生版本快照；只刷新 updatedAt。
     * 使用写锁避免与保存操作交错导致名称和内容版本不一致。</p>
     *
     * @param id      图纸主键
     * @param request 名称/电压等级/说明
     * @return 更新后的图纸详情
     * @throws ApiException 图纸不存在或已删除时抛出 404
     */
    @Transactional
    public DiagramDetail updateMeta(Long id, UpdateDiagramRequest request) {
        Diagram diagram = diagramRepository.findActiveForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));
        diagram.setName(request.name().trim());
        diagram.setVoltageLevel(trimToNull(request.voltageLevel()));
        diagram.setDescription(trimToNull(request.description()));
        diagram.setUpdatedAt(LocalDateTime.now());
        return toDetail(diagram);
    }

    /**
     * 保存画布文档。
     *
     * <p>流程：写锁读取图纸 → 校验客户端 revision → 校验/序列化文档 → 处理缩略图
     * → 更新计数、自增 revision → 写入版本快照。任一步失败都会回滚整个事务。</p>
     *
     * <p>缩略图的三种语义：{@code null} 保持原图不变；空字符串清除缩略图；
     * 非空 data URL 替换缩略图。缩略图必须是 {@code data:image/} 开头且不超过
     * 3MB，防止写入任意文本或超大图片。</p>
     *
     * @param id      图纸主键
     * @param request 完整文档、期望修订号、可选缩略图和变更说明
     * @return 保存后的图纸详情，revision 已自增
     * @throws ApiException 图纸不存在（404）、版本冲突（409）、文档/缩略图非法（400）
     */
    @Transactional
    public DiagramDetail save(Long id, SaveDiagramRequest request) {
        Diagram diagram = diagramRepository.findActiveForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));

        // 乐观锁校验：客户端必须基于最新修订号保存，否则说明其他窗口已经写入。
        if (request.revision() != diagram.getRevision()) {
            throw ApiException.conflict("图纸已在其他窗口更新（当前版本 " + diagram.getRevision()
                    + "），请重新载入后再保存");
        }

        JsonNode document = normalizeDocument(request.document());
        String json = writeDocument(document);
        String thumbnail = request.thumbnail();
        if (thumbnail != null && !thumbnail.isBlank()) {
            if (thumbnail.length() > MAX_THUMBNAIL_BYTES) {
                throw ApiException.badRequest("缩略图数据过大");
            }
            if (!thumbnail.startsWith("data:image/")) {
                throw ApiException.badRequest("缩略图格式无效");
            }
            diagram.setThumbnail(thumbnail);
        } else if (thumbnail != null) {
            // 显式传空字符串：用户主动清除缩略图。
            diagram.setThumbnail(null);
        }
        // thumbnail 为 null 时不做处理，保留上一次保存的缩略图。

        diagram.setDocumentJson(json);
        diagram.setNodeCount(countChildren(document, "nodes"));
        diagram.setEdgeCount(countChildren(document, "edges"));
        diagram.setRevision(diagram.getRevision() + 1);
        diagram.setUpdatedAt(LocalDateTime.now());

        String note = request.changeNote() == null || request.changeNote().isBlank()
                ? "保存于 " + LocalDateTime.now().format(SAVE_TIME)
                : request.changeNote().trim();
        addVersion(diagram, note);
        return toDetail(diagram);
    }

    /**
     * 复制图纸。
     *
     * <p>深拷贝画布 JSON 和缩略图，生成新编号和“原名称 · 副本”名称；修订号重置为 1，
     * 并写入一条版本说明记录来源编号。副本与原图完全独立，后续修改互不影响。</p>
     *
     * @param id 源图纸主键
     * @return 副本详情
     * @throws ApiException 源图纸不存在或已删除时抛出 404
     */
    @Transactional
    public DiagramDetail duplicate(Long id) {
        Diagram source = diagramRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));
        LocalDateTime now = LocalDateTime.now();
        Diagram copy = new Diagram();
        copy.setName(source.getName() + " · 副本");
        copy.setCode(nextCode());
        copy.setVoltageLevel(source.getVoltageLevel());
        copy.setDescription(source.getDescription());
        copy.setDocumentJson(source.getDocumentJson());
        copy.setThumbnail(source.getThumbnail());
        copy.setNodeCount(source.getNodeCount());
        copy.setEdgeCount(source.getEdgeCount());
        copy.setRevision(1);
        copy.setStatus("ACTIVE");
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        diagramRepository.save(copy);
        addVersion(copy, "复制自 " + source.getCode());
        return toDetail(copy);
    }

    /**
     * 软删除图纸。
     *
     * <p>只更新 status 和 updatedAt，不删除主表或版本表记录。删除后所有面向用户的
     * 查询都会过滤掉该图纸；如需恢复，可由运维直接改回 ACTIVE（当前未开放接口）。</p>
     *
     * @param id 图纸主键
     * @throws ApiException 图纸不存在或已删除时抛出 404
     */
    @Transactional
    public void delete(Long id) {
        Diagram diagram = diagramRepository.findActiveForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));
        diagram.setStatus("DELETED");
        diagram.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 查询最近 50 条版本摘要。
     *
     * <p>先确认图纸存在且未删除，再读取版本，避免通过已删除图纸的 id 探测历史。</p>
     *
     * @param id 图纸主键
     * @return 版本摘要列表，修订号从大到小
     * @throws ApiException 图纸不存在或已删除时抛出 404
     */
    @Transactional(readOnly = true)
    public List<VersionSummary> versions(Long id) {
        diagramRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));
        return versionRepository.findTop50ByDiagramIdOrderByRevisionDesc(id).stream()
                .map(v -> new VersionSummary(v.getId(), v.getRevision(), v.getChangeNote(), v.getCreatedAt()))
                .toList();
    }

    /**
     * 查询单个历史版本详情。
     *
     * <p>同时按 versionId 和 diagramId 查询，防止跨图纸读取版本。</p>
     *
     * @param diagramId 图纸主键
     * @param versionId 版本主键
     * @return 版本详情（含完整文档）
     * @throws ApiException 版本不存在时抛出 404
     */
    @Transactional(readOnly = true)
    public VersionDetail version(Long diagramId, Long versionId) {
        DiagramVersion version = versionRepository.findByIdAndDiagramId(versionId, diagramId)
                .orElseThrow(() -> ApiException.notFound("历史版本不存在"));
        return new VersionDetail(version.getId(), version.getRevision(), version.getChangeNote(),
                version.getCreatedAt(), readDocument(version.getDocumentJson()));
    }

    /**
     * 恢复历史版本。
     *
     * <p>恢复不是回退：先校验客户端 revision，再把历史文档写回主表，revision 继续
     * 自增，并生成一条“恢复至版本 Rn”的新快照。这样版本链始终单调递增，不会出现
     * 两个不同内容共用同一修订号的情况。</p>
     *
     * <p>恢复后清空缩略图，因为它仍然显示恢复前的画面；等待用户下一次保存时重新生成。</p>
     *
     * @param id        图纸主键
     * @param versionId 目标版本主键
     * @param revision  客户端当前修订号
     * @return 恢复后的图纸详情
     * @throws ApiException 图纸/版本不存在（404）、版本冲突（409）
     */
    @Transactional
    public DiagramDetail restore(Long id, Long versionId, int revision) {
        Diagram diagram = diagramRepository.findActiveForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("图纸不存在或已被删除"));
        if (revision != diagram.getRevision()) {
            throw ApiException.conflict("图纸已更新，请刷新后再恢复历史版本");
        }
        DiagramVersion version = versionRepository.findByIdAndDiagramId(versionId, id)
                .orElseThrow(() -> ApiException.notFound("历史版本不存在"));

        JsonNode restored = readDocument(version.getDocumentJson());
        diagram.setDocumentJson(version.getDocumentJson());
        diagram.setNodeCount(countChildren(restored, "nodes"));
        diagram.setEdgeCount(countChildren(restored, "edges"));
        // 缩略图仍显示恢复前的画面，先清空，等下一次保存重新生成。
        diagram.setThumbnail(null);
        diagram.setRevision(diagram.getRevision() + 1);
        diagram.setUpdatedAt(LocalDateTime.now());
        addVersion(diagram, "恢复至版本 R" + version.getRevision());
        return toDetail(diagram);
    }

    /**
     * 写入一条版本快照。
     *
     * <p>必须在主表 revision 已更新后调用，快照修订号与主表保持一致。调用方负责
     * 事务边界和文档内容；本方法只做对象组装与保存。</p>
     *
     * @param diagram 已更新修订号的图纸
     * @param note    变更说明
     */
    private void addVersion(Diagram diagram, String note) {
        DiagramVersion version = new DiagramVersion();
        version.setDiagram(diagram);
        version.setRevision(diagram.getRevision());
        version.setDocumentJson(diagram.getDocumentJson());
        version.setChangeNote(note);
        version.setCreatedAt(LocalDateTime.now());
        versionRepository.save(version);
    }

    /**
     * 生成全局唯一图纸编号。
     *
     * <p>格式：{@code WD-yyyyMMdd-XXXX}，其中 XXXX 来自 32 字符集。先查询再插入只能
     * 降低碰撞概率，真正的唯一性由数据库唯一索引兜底；极端并发下若插入冲突，会由
     * 全局异常处理器返回 409。最多重试 20 次，仍失败则视为系统性异常。</p>
     *
     * @return 新编号
     */
    private String nextCode() {
        String prefix = "WD-" + LocalDate.now().format(CODE_DATE) + "-";
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
            }
            String code = prefix + suffix;
            if (!diagramRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("无法生成唯一图纸编号");
    }

    /**
     * 校验并规范化画布文档。
     *
     * <p>依次检查：必须是 JSON 对象、序列化后不超过 8MB、必须包含 nodes 和 edges
     * 数组。这里先序列化一次用于测量字节数，调用方通常还会再序列化一次用于持久化；
     * 文档上限 8MB，重复序列化的开销可以接受。</p>
     *
     * @param document 客户端提交的文档
     * @return 原文档节点
     * @throws ApiException 结构或大小不合法时抛出 400
     */
    private JsonNode normalizeDocument(JsonNode document) {
        if (!document.isObject()) {
            throw ApiException.badRequest("图纸内容格式错误");
        }
        String json = writeDocument(document);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            throw ApiException.badRequest("图纸内容超过 8MB 限制");
        }
        if (!document.path("nodes").isArray() || !document.path("edges").isArray()) {
            throw ApiException.badRequest("图纸内容缺少 nodes 或 edges 数组");
        }
        return document;
    }

    /**
     * 生成默认空白画布。
     *
     * <p>默认 A3 横向（1600×1131）、20px 网格、显示网格和标题栏；标题取图纸名称。
     * 结构与前端保存的文档保持一致，前端打开后可直接进入编辑状态。</p>
     *
     * @param title 图纸名称，可为 null
     * @return 默认文档
     */
    private JsonNode defaultDocument(String title) {
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

        ObjectNode meta = doc.putObject("meta");
        meta.put("title", title == null ? "未命名接线图" : title.trim());
        meta.put("drawingNo", "");
        meta.put("project", "");
        meta.put("voltageLevel", "");
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
     *
     * @param document 文档节点
     * @return JSON 字符串
     * @throws ApiException 序列化失败时抛出 400
     */
    private String writeDocument(JsonNode document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("图纸内容无法序列化");
        }
    }

    /**
     * 将数据库中的 JSON 字符串解析为树。
     *
     * <p>数据由本服务写入，正常情况下必然合法；解析失败说明数据已损坏，属于服务端
     * 问题，因此返回 500 而不是 400。</p>
     *
     * @param json 数据库中的文档字符串
     * @return JSON 树
     * @throws ApiException 数据损坏时抛出 500
     */
    private JsonNode readDocument(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图纸数据损坏");
        }
    }

    /**
     * 统计文档中某个数组字段的元素数量。
     *
     * @param document 文档
     * @param field    {@code nodes} 或 {@code edges}
     * @return 元素数量；字段缺失或不是数组时返回 0
     */
    private int countChildren(JsonNode document, String field) {
        JsonNode array = document.path(field);
        return array.isArray() ? array.size() : 0;
    }

    /**
     * 去除首尾空白，空字符串转为 null。
     *
     * <p>数据库列长度有限，这里额外截断到 500 字符作为防御；正常请求已经由 DTO
     * 上的 {@code @Size} 校验拦截，不会走到截断逻辑。</p>
     *
     * @param value 原始值
     * @return 处理后的值或 null
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    /**
     * 实体转列表摘要。
     *
     * @param d 图纸实体
     * @return 摘要 DTO
     */
    private DiagramSummary toSummary(Diagram d) {
        return new DiagramSummary(d.getId(), d.getName(), d.getCode(), d.getVoltageLevel(),
                d.getDescription(), d.getNodeCount(), d.getEdgeCount(), d.getRevision(),
                d.getThumbnail(), d.getCreatedAt(), d.getUpdatedAt());
    }

    /**
     * 实体转详情，解析存储的 JSON 文档。
     *
     * @param d 图纸实体
     * @return 详情 DTO
     */
    private DiagramDetail toDetail(Diagram d) {
        return new DiagramDetail(d.getId(), d.getName(), d.getCode(), d.getVoltageLevel(),
                d.getDescription(), d.getRevision(), readDocument(d.getDocumentJson()),
                d.getThumbnail(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
