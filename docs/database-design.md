# 数据库表设计说明

本文档描述 GRIDLINE 接线图设计器的数据库结构、字段含义、索引设计以及读写策略。
表结构的唯一事实来源是 Flyway 迁移脚本：

```text
backend/src/main/resources/db/migration/V1__init_schema.sql
```

应用使用 `spring.jpa.hibernate.ddl-auto=validate`，启动时只校验实体映射与真实表结构
是否一致，不会自动改表。**任何表结构变更都必须新增迁移脚本（如
`V2__xxx.sql`），不能修改已经执行过的 `V1`，否则 Flyway 校验和会失败。**

---

## 1. 总体结构

数据库：MySQL 8.0，库名默认 `switchgear_wiring`，字符集 `utf8mb4`，排序规则
`utf8mb4_unicode_ci`，存储引擎 InnoDB。

```text
┌─────────────────────────────┐
│       wiring_diagram        │  图纸主表（当前状态）
│─────────────────────────────│
│ PK  id                      │
│ UK  code                    │
│     name / voltage_level    │
│     description             │
│     document_json  (LONGTEXT)│
│     thumbnail      (LONGTEXT)│
│     node_count / edge_count │
│     revision                │
│     status                  │
│     created_at / updated_at │
└──────────────┬──────────────┘
               │ 1 : N
               │ diagram_id (FK, ON DELETE CASCADE)
               ▼
┌─────────────────────────────┐
│   wiring_diagram_version    │  历史版本快照（只追加）
│─────────────────────────────│
│ PK  id                      │
│ FK  diagram_id              │
│     revision                │
│     document_json  (LONGTEXT)│
│     change_note             │
│     created_at              │
│ UK  (diagram_id, revision)  │
└─────────────────────────────┘
```

两张表是“当前态 + 历史态”的关系：

- `wiring_diagram` 只保存每张图**最新**的文档，编辑器打开时读取这一份；
- `wiring_diagram_version` 保存每次保存时的**完整快照**，用于历史查看与恢复；
- 一张图纸最多展示最近 50 个版本，但数据库保留全部历史。

为什么画布不拆成节点表、连线表？

1. 画布文档由前端模型直接序列化，字段多且迭代频繁，拆表会导致每次前端模型变动都要
   改数据库；
2. 编辑器始终整份读取、整份保存，没有按单个节点做 SQL 查询的需求；
3. 完整 JSON 快照恢复简单，不依赖中间版本是否完整。

因此只在主表冗余 `node_count`、`edge_count` 两列，满足工作台统计，避免统计时解析
所有 JSON。

---

## 2. 图纸主表 `wiring_diagram`

| 字段 | 类型 | 空 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `name` | `VARCHAR(120)` | 否 | — | 图纸名称，列表搜索字段 |
| `code` | `VARCHAR(64)` | 否 | — | 全局唯一编号，格式 `WD-yyyyMMdd-XXXX` |
| `voltage_level` | `VARCHAR(32)` | 是 | `NULL` | 电压等级，如 `10kV`、`0.4kV` |
| `description` | `VARCHAR(500)` | 是 | `NULL` | 图纸说明 |
| `document_json` | `LONGTEXT` | 否 | — | 完整画布文档，UTF-8 JSON |
| `thumbnail` | `LONGTEXT` | 是 | `NULL` | 列表缩略图 data URL |
| `node_count` | `INT` | 否 | `0` | 文档中节点数量，保存时重算 |
| `edge_count` | `INT` | 否 | `0` | 文档中连线数量，保存时重算 |
| `revision` | `INT` | 否 | `1` | 当前修订号，乐观锁依据 |
| `status` | `VARCHAR(20)` | 否 | `ACTIVE` | `ACTIVE` 正常 / `DELETED` 已软删除 |
| `created_at` | `DATETIME(3)` | 否 | — | 创建时间，毫秒精度 |
| `updated_at` | `DATETIME(3)` | 否 | — | 最后更新时间，列表默认倒序 |

### 2.1 编号 `code`

- 由服务端生成，不由前端传入，避免重复或恶意指定；
- 前缀为 `WD-` + 服务器当天日期（`Asia/Shanghai`）；
- 后 4 位从 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` 中随机取，去掉易混淆的
  `I/O/0/1`；
- 生成时先查重，数据库唯一索引 `uk_wiring_diagram_code` 兜底；
- 编号不会随复制、恢复改变：复制生成新编号，恢复沿用原图纸编号。

### 2.2 画布文档 `document_json`

文档采用版本化结构，当前 `schemaVersion = 1`：

```json
{
  "schemaVersion": 1,
  "paper": {
    "size": "A3",
    "orientation": "landscape",
    "width": 1600,
    "height": 1131
  },
  "settings": {
    "gridSize": 20,
    "snapToGrid": true,
    "showGrid": true,
    "showTitleBlock": true
  },
  "meta": {
    "title": "10kV 进线柜",
    "drawingNo": "",
    "project": "",
    "voltageLevel": "",
    "designer": "",
    "checker": "",
    "date": "2026-11-03",
    "company": "",
    "scale": "NTS"
  },
  "nodes": [],
  "edges": []
}
```

约束（服务端校验）：

- 顶层必须是 JSON 对象；
- 必须包含 `nodes` 与 `edges` 数组，否则返回 HTTP 400；
- 序列化为 UTF-8 后不超过 8 MB；
- `node_count`、`edge_count` 由服务端按数组长度重新计算，前端传入的值不参与持久化。

`nodes` 中每个元素描述一个设备实例（类型、位置、尺寸、旋转、样式、标签、锁定状态
等），`edges` 描述一条导线（起点/终点端口、路由方式、线型、颜色、箭头等）。具体字段
由前端 `src/types/diagram.ts` 定义，后端按不透明 JSON 存储，只校验外层结构，从而允许
前端在不改表的前提下迭代画布模型。

### 2.3 缩略图 `thumbnail`

- 浏览器端生成，格式为 `data:image/...;base64,...`；
- 服务端要求以 `data:image/` 开头，字符长度不超过 3 MB；
- 保存时传 `null` 表示保持原图，传空字符串表示清除；恢复历史版本时会自动清空，
  避免列表显示旧画面。

### 2.4 修订号 `revision`

- 新建、复制后的修订号为 `1`；
- 每次保存文档或恢复历史版本时 `revision = revision + 1`；
- 修改名称/说明等元信息**不**增加修订号；
- 前端保存时必须回传当前修订号，服务端在写锁事务中比对，不一致返回 HTTP 409。

### 2.5 状态 `status`

- `ACTIVE`：正常，可查询、可编辑；
- `DELETED`：软删除，所有业务查询都会过滤掉；
- 删除不物理删除主表和版本表，便于审计与误删恢复；
- 外键的 `ON DELETE CASCADE` 只作为物理清理时的兜底，正常业务不会触发。

### 2.6 索引

| 索引 | 字段 | 用途 |
| --- | --- | --- |
| `PRIMARY` | `id` | 主键查询、外键关联 |
| `uk_wiring_diagram_code` | `code` | 编号唯一性，生成编号时快速查重 |
| `idx_wiring_diagram_status_updated` | `(status, updated_at)` | 列表按状态过滤 + 更新时间倒序；统计 ACTIVE 数量 |
| `idx_wiring_diagram_name` | `name` | 名称前缀/排序辅助 |

说明：关键字搜索使用 `LIKE '%keyword%'`（前后都有通配符），B-Tree 索引无法有效
利用，因此搜索在数据量较大时会走全表扫描。当前图纸量级较小可以接受；若未来达到
十万级以上，建议引入 MySQL 全文索引或 Elasticsearch。

---

## 3. 历史版本表 `wiring_diagram_version`

| 字段 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 主键，自增 |
| `diagram_id` | `BIGINT` | 否 | 所属图纸，外键 → `wiring_diagram.id` |
| `revision` | `INT` | 否 | 对应修订号 |
| `document_json` | `LONGTEXT` | 否 | 该版本的完整画布文档 |
| `change_note` | `VARCHAR(255)` | 是 | 变更说明 |
| `created_at` | `DATETIME(3)` | 否 | 快照时间 |

### 3.1 写入时机

| 操作 | 是否写快照 | 修订号 |
| --- | --- | --- |
| 新建图纸 | 是，备注“创建图纸” | 1 |
| 保存文档 | 是，备注用户填写或“保存于 ...” | +1 |
| 复制图纸 | 是，备注“复制自 源编号” | 副本从 1 开始 |
| 修改名称/说明 | 否 | 不变 |
| 恢复历史版本 | 是，备注“恢复至版本 Rn” | +1 |
| 软删除 | 否 | 不变 |

### 3.2 唯一约束与查询

- `uk_diagram_version (diagram_id, revision)`：同一图纸同一修订号只能有一条快照，
  防止并发重复写入；
- `idx_version_diagram_created (diagram_id, created_at)`：按图纸查版本时间线；
- 版本列表查询 `ORDER BY revision DESC LIMIT 50`，可利用唯一索引的 `(diagram_id,
  revision)` 前缀；
- 版本详情同时按 `version_id + diagram_id` 查询，防止跨图纸读取。

### 3.3 恢复语义

恢复不是把 `revision` 回退，而是：

1. 校验客户端 `revision` 与主表一致；
2. 把目标版本的 `document_json` 写回主表；
3. `revision + 1`；
4. 写入一条新快照，备注“恢复至版本 Rn”；
5. 清空缩略图。

这样版本链单调递增，不会出现两个不同内容共用一个修订号，历史记录始终可追溯。

### 3.4 外键与删除

```sql
CONSTRAINT fk_version_diagram FOREIGN KEY (diagram_id)
    REFERENCES wiring_diagram (id) ON DELETE CASCADE
```

- 正常业务只做软删除，因此外键不会触发；
- 若运维物理删除主表记录，其所有版本快照会级联删除，避免孤儿数据；
- 版本表没有反向的 `@OneToMany` 集合，实体只保留 `@ManyToOne(LAZY)`，避免加载
  图纸时把全部历史读进内存。

---

## 4. 并发与事务

### 4.1 写操作串行化

保存、改元信息、删除、恢复都通过：

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select d from Diagram d where d.id = :id and d.status = 'ACTIVE'")
Optional<Diagram> findActiveForUpdate(Long id);
```

生成 `SELECT ... FOR UPDATE`，锁住当前图纸行直到事务提交。这样“读取 revision →
比较 → 自增 → 写快照”不会与其他事务交错执行。

### 4.2 乐观锁

仅靠行锁仍不能解决“用户 A 打开旧页面、用户 B 保存后，A 再保存覆盖”的问题，因此
请求体必须带 `revision`：

- 一致：正常保存；
- 不一致：返回 HTTP 409，提示用户重新载入。

前端把最新 `revision` 保存在编辑器状态中，每次保存成功后用响应里的新值更新。

### 4.3 事务边界

- 所有写方法标注 `@Transactional`，读方法标注 `@Transactional(readOnly = true)`；
- 主表更新与版本快照插入在同一事务中，任一失败全部回滚；
- `spring.jpa.open-in-view=false`，避免请求结束后仍持有数据库连接。

---

## 5. 容量与性能

| 项目 | 限制 | 位置 |
| --- | --- | --- |
| 单份画布 JSON | 8 MB（UTF-8 字节） | `DiagramService.MAX_DOCUMENT_BYTES` |
| 缩略图 | 3 MB（字符串长度） | `DiagramService.MAX_THUMBNAIL_BYTES` |
| 列表每页 | 最多 60 条 | `DiagramService.list` |
| 版本列表 | 最近 50 条 | `DiagramVersionRepository` |
| 连接池 | 最大 10，最小空闲 2 | `application.yml` |

容量估算：

- `LONGTEXT` 理论上限 4 GB，应用层 8 MB 限制远低于数据库上限；
- 若每张图 20 次保存、每次快照 200 KB，单图历史约 4 MB；1000 张图约 4 GB，
  仍在单库可维护范围；
- 历史版本只追加不修改，长期使用建议按季度归档较早版本，或对 `document_json`
  做压缩后存储。

---

## 6. 时区与时间

- JVM/Hibernate JDBC 时区：`Asia/Shanghai`；
- 数据库列使用 `DATETIME(3)`（不带时区），由应用层 `LocalDateTime.now()` 赋值；
- MySQL 连接串包含 `serverTimezone=Asia/Shanghai`；
- 前后端 JSON 日期通过 Jackson 输出 ISO-8601 字符串（`write-dates-as-timestamps=false`）。

生产部署时需保证 JVM、MySQL 会话时区、连接串三者一致，否则“最近 7 天更新”等
时间条件会出现偏差。

---

## 7. Flyway 迁移规范

- 脚本目录：`backend/src/main/resources/db/migration`；
- 命名：`V{序号}__{说明}.sql`，例如 `V2__add_diagram_tags.sql`；
- 已执行的脚本不可修改，否则校验和变化会导致应用启动失败；
- 新增字段优先允许 `NULL` 或提供默认值，避免存量数据迁移失败；
- 大表加索引/改字段应评估锁表时间，必要时使用在线 DDL；
- 回滚不使用 `undo` 脚本，采用“新增一个向前修复的迁移”策略。

查看迁移状态：

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

---

## 8. 备份与恢复建议

1. 每日全量备份：`mysqldump --single-transaction --routines --triggers`；
2. 开启 binlog，支持按时间点恢复；
3. 恢复演练应同时验证 `wiring_diagram` 与 `wiring_diagram_version` 的一致性；
4. 软删除数据仍占用空间，可按业务策略定期归档 `status='DELETED'` 的记录；
5. 不要手工修改 `revision`，否则会破坏乐观锁与版本唯一约束。
