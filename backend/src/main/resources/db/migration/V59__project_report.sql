-- =====================================================
-- V59: 审计报告台账 + 报销费用统计
-- 1) 项目登记报告成果：报告文号/出具日期/签发合伙人/备注
-- =====================================================

ALTER TABLE project ADD COLUMN IF NOT EXISTS report_no           VARCHAR(100);
ALTER TABLE project ADD COLUMN IF NOT EXISTS report_date         DATE;
ALTER TABLE project ADD COLUMN IF NOT EXISTS report_partner_name VARCHAR(50);
ALTER TABLE project ADD COLUMN IF NOT EXISTS report_remark       VARCHAR(500);
