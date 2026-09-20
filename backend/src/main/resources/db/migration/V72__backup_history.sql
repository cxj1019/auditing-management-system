-- V72: 备份历史表（含附件的完整 ZIP 备份）+ 菜单更名
CREATE TABLE IF NOT EXISTS backup_history (
    id          BIGSERIAL PRIMARY KEY,
    object_path VARCHAR(300) NOT NULL UNIQUE,
    size_bytes  BIGINT,
    file_count  INT,
    create_time TIMESTAMP DEFAULT NOW()
);

UPDATE sys_menu SET name = 'AI 与备份' WHERE id = 205;
