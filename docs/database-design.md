# 数据库设计说明（简化版）

## 1. 设计目标

当前版本只服务于“图纸库 + 空白画布编辑器”这一条主流程：

- 新建图纸直接进入空白画布，不再按柜列配置自动生成；
- 不保存历史版本，不提供版本恢复；
- 不区分软删除状态，删除即物理删除；
- 不使用乐观锁，多个窗口同时保存时以最后一次写入为准。

因此整个系统只需要 **一张表**：`wiring_diagram`。原先的
`wiring_diagram_version` 版本表、`revision` 乐观锁字段和 `status` 软删除字段均已移除。

## 2. 数据库

| 项目 | 值 |
| --- | --- |
| 数据库 | MySQL 8.0 |
| 库名 | `gridline_wiring`（旧库 `switchgear_wiring` 不做修改） |
| 字符集 | `utf8mb4` / `utf8mb4_unicode_ci` |
| 表数量 | 1 |
| 建表方式 | 应用启动时执行 `src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`） |

连接参数支持环境变量覆盖：

```bash
DB_URL='jdbc:mysql://localhost:3306/gridline_wiring?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
DB_USERNAME=root
DB_PASSWORD=root
```

## 3. 表结构：wiring_diagram

| 字段 | 类型 | 允许空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | 否 | 自增 | 主键 |
| `name` | VARCHAR(120) | 否 | — | 图纸名称，列表搜索字段 |
| `voltage_level` | VARCHAR(32) | 是 | NULL | 电压等级，如 10kV、0.4kV |
| `description` | VARCHAR(500) | 是 | NULL | 图纸说明 |
| `document_json` | LONGTEXT | 否 | — | 完整画布文档 JSON，见第 4 节 |
| `thumbnail` | LONGTEXT | 是 | NULL | 图纸库缩略图，`data:image/...;base64,...` |
| `node_count` | INT | 否 | 0 | 节点数量，保存时由文档重新统计 |
| `edge_count` | INT | 否 | 0 | 导线数量，保存时由文档重新统计 |
| `created_at` | DATETIME(3) | 否 | — | 创建时间，MyBatis-Plus 自动填充 |
| `updated_at` | DATETIME(3) | 否 | — | 最后更新时间，列表按此倒序 |

索引：

| 名称 | 类型 | 字段 | 用途 |
| --- | --- | --- | --- |
| `PRIMARY` | 主键 | `id` | 主键查询 |
| `idx_wiring_diagram_updated_at` | 普通索引 | `updated_at` | 图纸库按更新时间倒序分页 |
| `idx_wiring_diagram_name` | 普通索引 | `name` | 名称关键字搜索 |

## 4. document_json 结构

画布内容整份存储，结构由前端定义，后端只校验顶层必需字段：

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
    "showTitleBlock": true,
    "showLabels": false
  },
  "meta": {
    "title": "10kV 配电室一次接线图",
    "drawingNo": "",
    "project": "",
    "voltageLevel": "10kV",
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

校验规则：

- 必须是 JSON 对象；
- 序列化后的 UTF-8 字节数不超过 **8MB**；
- 必须包含 `nodes` 和 `edges` 两个数组，否则返回 HTTP 400。

## 5. 字段与约束说明

### 5.1 为什么整存 JSON

画布节点、导线、图框设置与前端模型强耦合，字段迭代频繁。整份 JSON 存取可以避免
每次新增符号属性都改表；代价是无法按节点做 SQL 查询，因此冗余 `node_count`、
`edge_count` 供统计使用。

### 5.2 缩略图

缩略图由浏览器在保存前生成，后端只接受 `data:image/` 开头且不超过 3MB 的字符串。
保存请求中：

- `thumbnail` 为 `null`：保持原缩略图；
- `thumbnail` 为空字符串：清除缩略图；
- 非空合法 data URL：替换缩略图。

### 5.3 删除

`DELETE /api/diagrams/{id}` 直接执行 `DELETE FROM wiring_diagram WHERE id = ?`。
没有 `status` 字段，也不存在回收站；前端已明确提示“删除后无法恢复”。

### 5.4 并发

没有 `revision` 字段，保存请求不再携带版本号。两个窗口同时编辑同一张图纸时，
后提交的请求覆盖先提交的内容。这是本次简化有意接受的行为。

### 5.5 时间字段

`created_at`、`updated_at` 由 `MyMetaObjectHandler` 在 MyBatis-Plus 插入/更新时自动
填充，数据库列也保留默认约束之外的显式 `NOT NULL`，避免脏数据。

## 6. 旧库处理

旧库 `switchgear_wiring` 仍保留原表与数据，不做删除或结构修改。若需要把旧图纸
迁移到新库，可按下列 SQL 复制当前有效图纸（旧库存在 `status` 字段）：

```sql
INSERT INTO gridline_wiring.wiring_diagram
    (name, voltage_level, description, document_json, thumbnail,
     node_count, edge_count, created_at, updated_at)
SELECT name, voltage_level, description, document_json, thumbnail,
       node_count, edge_count, created_at, updated_at
FROM switchgear_wiring.wiring_diagram
WHERE status = 'ACTIVE';
```

旧表存在 `code` 列时无需迁移该列，新表以自增主键 `id` 标识图纸。
