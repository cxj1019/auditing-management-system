-- V35: 合同字号前缀统一为「迈伊兹××约」，项目类型与合同业务类型对齐

-- 字号前缀调整（流水与年份规则不变）
UPDATE contract_no_type SET prefix = '迈伊兹审约', remark = '审计业务合同，如 迈伊兹审约(2026)第0001号', update_time = CURRENT_TIMESTAMP WHERE contract_type = '审计';
UPDATE contract_no_type SET prefix = '迈伊兹验约', remark = '验资业务合同，如 迈伊兹验约(2026)第0001号', update_time = CURRENT_TIMESTAMP WHERE contract_type = '验资';
UPDATE contract_no_type SET prefix = '迈伊兹咨约', remark = '税务咨询类合同，如 迈伊兹咨约(2026)第0001号', update_time = CURRENT_TIMESTAMP WHERE contract_type = '税务咨询';
UPDATE contract_no_type SET prefix = '迈伊兹代约', remark = '代理记账类合同，如 迈伊兹代约(2026)第0001号', update_time = CURRENT_TIMESTAMP WHERE contract_type = '代理记账';
UPDATE contract_no_type SET prefix = '迈伊兹评约', remark = '评估业务合同，如 迈伊兹评约(2026)第0001号', update_time = CURRENT_TIMESTAMP WHERE contract_type = '评估';
UPDATE contract_no_type SET prefix = '迈伊兹商约', remark = '其他类合同，如 迈伊兹商约(2026)第0001号', update_time = CURRENT_TIMESTAMP WHERE contract_type = '其他';

-- 项目类型与合同业务类型对齐（历史值归并到新枚举）
UPDATE project SET type = '审计' WHERE type IN ('Audit', 'audit');
UPDATE project SET type = '税务咨询' WHERE type IN ('税务', '咨询');
