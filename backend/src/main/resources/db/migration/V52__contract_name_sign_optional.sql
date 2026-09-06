-- =====================================================
-- V52: 合同名称不再手填（留空时以合同字号代替）；签约日期改为可选
-- =====================================================

ALTER TABLE contract ALTER COLUMN name DROP NOT NULL;
ALTER TABLE contract ALTER COLUMN sign_date DROP NOT NULL;
