-- =====================================================
-- V47: 报销单归属改为按用户 ID 判断
-- 用户名（即邮箱）在编辑用户后可能变化（如大小写），
-- 按用户名快照判断归属会导致历史单据丢失申请人权限，
-- 故增加 applicant_id 并按当前用户名回填历史数据
-- =====================================================

ALTER TABLE reimbursement ADD COLUMN IF NOT EXISTS applicant_id BIGINT;

UPDATE reimbursement r
SET applicant_id = u.id
FROM sys_user u
WHERE r.applicant_id IS NULL
  AND lower(r.applicant_username) = lower(u.username);

CREATE INDEX IF NOT EXISTS idx_reimbursement_applicant_id ON reimbursement (applicant_id);
