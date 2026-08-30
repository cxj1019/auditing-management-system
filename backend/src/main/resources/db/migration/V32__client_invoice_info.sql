-- V32: 客户开票信息（增值税发票开票六要素）
ALTER TABLE client ADD COLUMN invoice_title        VARCHAR(200);
ALTER TABLE client ADD COLUMN invoice_tax_no       VARCHAR(50);
ALTER TABLE client ADD COLUMN invoice_bank_name    VARCHAR(200);
ALTER TABLE client ADD COLUMN invoice_bank_account VARCHAR(100);
ALTER TABLE client ADD COLUMN invoice_address      VARCHAR(500);
ALTER TABLE client ADD COLUMN invoice_phone        VARCHAR(50);
