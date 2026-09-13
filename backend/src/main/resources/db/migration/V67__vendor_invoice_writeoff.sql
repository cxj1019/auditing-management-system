-- V67: 对公付款的进项发票与预付核销
CREATE TABLE IF NOT EXISTS vendor_invoice (
    id            BIGSERIAL PRIMARY KEY,
    vendor_name   VARCHAR(200) NOT NULL,
    invoice_no    VARCHAR(50),
    type          VARCHAR(20)  NOT NULL DEFAULT '增值税专用发票',
    tax_rate      NUMERIC(5,2),
    amount        NUMERIC(14,2) NOT NULL,
    amount_ex_tax NUMERIC(14,2),
    tax_amount    NUMERIC(14,2),
    invoice_date  DATE,
    project_id    BIGINT,
    remark        VARCHAR(500),
    create_by     VARCHAR(50),
    creator_name  VARCHAR(50),
    create_time   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vendor_invoice_project ON vendor_invoice(project_id);

-- 付款关联进项发票：NULL = 预付（尚未取得发票）
ALTER TABLE vendor_payment ADD COLUMN IF NOT EXISTS vendor_invoice_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_vendor_payment_invoice ON vendor_payment(vendor_invoice_id);
