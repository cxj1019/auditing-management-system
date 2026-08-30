-- V38: 发票号码可后补（登记时可不填；唯一约束保留，空号码不限数量）
ALTER TABLE invoice ALTER COLUMN invoice_no DROP NOT NULL;
