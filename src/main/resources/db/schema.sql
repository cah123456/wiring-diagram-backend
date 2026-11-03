-- ============================================================================
-- GRIDLINE 接线图数据库（简化版）
--
-- 设计原则：
--   1. 一张图纸只保存一条当前记录，不再保存历史版本；
--   2. 删除采用物理删除，不做软删除，因此没有 status 字段；
--   3. 没有乐观锁 revision，保存为最后一次写入生效；
--   4. 画布内容整体保存为 JSON，节点/导线数量冗余存储用于统计。
--
-- 脚本使用 CREATE TABLE IF NOT EXISTS，应用启动时由 spring.sql.init 自动执行，
-- 重复启动不会破坏已有数据。数据库名默认 gridline_wiring，可通过 DB_URL 覆盖。
-- ============================================================================
CREATE TABLE IF NOT EXISTS wiring_diagram (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(120)    NOT NULL                COMMENT '图纸名称',
    voltage_level   VARCHAR(32)     NULL                    COMMENT '电压等级，如 10kV',
    description     VARCHAR(500)    NULL                    COMMENT '图纸说明',
    document_json   LONGTEXT        NOT NULL                COMMENT '完整画布文档 JSON（paper/settings/meta/nodes/edges）',
    thumbnail       LONGTEXT        NULL                    COMMENT '列表缩略图 data URL',
    node_count      INT             NOT NULL DEFAULT 0      COMMENT '节点数量，保存时由文档重新统计',
    edge_count      INT             NOT NULL DEFAULT 0      COMMENT '导线数量，保存时由文档重新统计',
    created_at      DATETIME(3)     NOT NULL                COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL                COMMENT '最后更新时间',
    PRIMARY KEY (id),
    KEY idx_wiring_diagram_updated_at (updated_at),
    KEY idx_wiring_diagram_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接线图图纸主表';
