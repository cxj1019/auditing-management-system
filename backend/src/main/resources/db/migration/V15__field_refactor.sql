-- =====================================================
-- V15: 字段调整——负责人语义精确化、合同客户随项目
-- =====================================================

-- 项目：owner_name 拆分为项目经理与现场负责人
ALTER TABLE project RENAME COLUMN owner_name TO manager_name;
ALTER TABLE project ADD COLUMN site_leader_name VARCHAR(50) NOT NULL DEFAULT '';

-- 合同：客户随项目带出（删除独立列）；负责人改为合同保管人
ALTER TABLE contract DROP COLUMN client_name;
ALTER TABLE contract RENAME COLUMN owner_name TO keeper_name;
