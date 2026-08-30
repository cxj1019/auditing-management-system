-- V45: 合同服务期间改为可选（可不约定期间）
ALTER TABLE contract ALTER COLUMN service_start DROP NOT NULL;
ALTER TABLE contract ALTER COLUMN service_end DROP NOT NULL;
