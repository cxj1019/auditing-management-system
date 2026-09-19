-- V71: 报销单/对公付款单软删除（回收站可恢复）
ALTER TABLE reimbursement ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE vendor_payment ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_reimbursement_deleted ON reimbursement(deleted);
