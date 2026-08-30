-- V39: 发票金额拆分（不含税金额 / 税额 / 价税合计）
ALTER TABLE invoice ADD COLUMN amount_ex_tax NUMERIC(18,2);
ALTER TABLE invoice ADD COLUMN tax_amount NUMERIC(18,2);

COMMENT ON COLUMN invoice.amount IS '价税合计（元）';
COMMENT ON COLUMN invoice.amount_ex_tax IS '不含税金额（元）';
COMMENT ON COLUMN invoice.tax_amount IS '税额（元）';
