-- V42: 发票支持外币（境外客户），开票时抓取中国银行外汇牌价折算
ALTER TABLE invoice ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT '人民币';
ALTER TABLE invoice ADD COLUMN foreign_amount NUMERIC(18,2);
ALTER TABLE invoice ADD COLUMN exchange_rate NUMERIC(12,4);
ALTER TABLE invoice ADD COLUMN rate_publish_time VARCHAR(40);

COMMENT ON COLUMN invoice.currency IS '币种：人民币/美元/日元/欧元/港币/英镑';
COMMENT ON COLUMN invoice.foreign_amount IS '外币金额';
COMMENT ON COLUMN invoice.exchange_rate IS '中国银行牌价（每 100 外币兑人民币）';
