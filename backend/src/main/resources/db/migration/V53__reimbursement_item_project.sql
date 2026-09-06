-- =====================================================
-- V53: 报销明细行增强
-- 1) project_id：按行归集项目（同一报销单费用可分属不同项目），
--    归集时行项目优先，未填回退单头 project_id
-- 2) billable：标记垫付性质、可向客户收取的费用
-- =====================================================

ALTER TABLE reimbursement_item ADD COLUMN IF NOT EXISTS project_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_reimbursement_item_project ON reimbursement_item (project_id);

ALTER TABLE reimbursement_item ADD COLUMN IF NOT EXISTS billable BOOLEAN NOT NULL DEFAULT FALSE;
