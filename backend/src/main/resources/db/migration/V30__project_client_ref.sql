-- V26: 项目关联客户（替代文本字段）
ALTER TABLE project ADD COLUMN client_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE project DROP COLUMN client_name;
