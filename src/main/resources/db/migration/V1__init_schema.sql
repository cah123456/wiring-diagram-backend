CREATE TABLE IF NOT EXISTS wiring_diagram (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(120)    NOT NULL,
    code            VARCHAR(64)     NOT NULL,
    voltage_level   VARCHAR(32)     NULL,
    description     VARCHAR(500)    NULL,
    document_json   LONGTEXT        NOT NULL,
    thumbnail       LONGTEXT        NULL,
    node_count      INT             NOT NULL DEFAULT 0,
    edge_count      INT             NOT NULL DEFAULT 0,
    revision        INT             NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wiring_diagram_code (code),
    KEY idx_wiring_diagram_status_updated (status, updated_at),
    KEY idx_wiring_diagram_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wiring_diagram_version (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    diagram_id      BIGINT          NOT NULL,
    revision        INT             NOT NULL,
    document_json   LONGTEXT        NOT NULL,
    change_note     VARCHAR(255)    NULL,
    created_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diagram_version (diagram_id, revision),
    KEY idx_version_diagram_created (diagram_id, created_at),
    CONSTRAINT fk_version_diagram FOREIGN KEY (diagram_id)
        REFERENCES wiring_diagram (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
