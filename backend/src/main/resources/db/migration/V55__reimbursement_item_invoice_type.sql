-- =====================================================
-- V55: 报销明细行发票类型与税率
-- invoice_type: none-不涉及 vat_general-增值税普通发票 vat_special-增值税专用发票
-- 增值税专用发票必须填写税率（应用层校验）
-- 历史 is_vat_invoice=true 的行回填为增值税普通发票，并保持旧列同步
-- =====================================================

ALTER TABLE reimbursement_item ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,2);
ALTER TABLE reimbursement_item ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(20) NOT NULL DEFAULT 'none';

UPDATE reimbursement_item SET invoice_type = 'vat_general' WHERE invoice_type = 'none' AND is_vat_invoice = TRUE;
