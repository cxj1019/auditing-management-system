-- V34: 字号前缀调整（咨询/代理改为迈伊兹抬头），补齐其他/评估两类字号
UPDATE contract_no_type
SET prefix = '迈伊兹咨', remark = '税务咨询类合同，如 迈伊兹咨(2026)第0001号', update_time = CURRENT_TIMESTAMP
WHERE contract_type = '税务咨询';

UPDATE contract_no_type
SET prefix = '迈伊兹代', remark = '代理记账类合同，如 迈伊兹代(2026)第0001号', update_time = CURRENT_TIMESTAMP
WHERE contract_type = '代理记账';

INSERT INTO contract_no_type (type_char, prefix, contract_type, remark, create_time, update_time) VALUES
('商', '迈伊兹商', '其他', '其他类合同，如 迈伊兹商(2026)第0001号', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('评', '迈伊兹评', '评估', '评估业务合同，如 迈伊兹评(2026)第0001号', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
