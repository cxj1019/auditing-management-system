-- =====================================================
-- V51: 合同金额区分含税/不含税
-- amount 为价税合计（含税），新增税率与不含税金额、税额
-- =====================================================

ALTER TABLE contract ADD COLUMN IF NOT EXISTS tax_rate      NUMERIC(5,2);
ALTER TABLE contract ADD COLUMN IF NOT EXISTS amount_ex_tax NUMERIC(18,2);
ALTER TABLE contract ADD COLUMN IF NOT EXISTS tax_amount    NUMERIC(18,2);
