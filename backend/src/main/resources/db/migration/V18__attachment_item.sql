-- =====================================================
-- V18: 发票附件挂载到费用明细行
-- =====================================================

ALTER TABLE reimbursement_attachment ADD COLUMN item_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_reimbursement_attachment_item ON reimbursement_attachment (item_id);

COMMENT ON COLUMN reimbursement_attachment.item_id IS '关联费用明细行 ID（NULL 表示挂单整体）';
