-- =====================================================
-- V48: 数据权限模型调整——客户不再归属部门,项目归属部门
-- 1) project 新增 dept_id:按创建人当前部门回填,无部门的按客户部门兜底
-- 2) client 删除 dept_id:客户全员可见,部门隔离改由项目承担
-- =====================================================

ALTER TABLE project ADD COLUMN IF NOT EXISTS dept_id BIGINT;

-- 优先按项目创建人的当前部门回填
UPDATE project p
SET dept_id = u.dept_id
FROM sys_user u
WHERE p.dept_id IS NULL
  AND u.dept_id IS NOT NULL
  AND lower(p.create_by) = lower(u.username);

-- 无部门创建人的项目按客户归属部门兜底（须在 client 删除该列前执行）
UPDATE project p
SET dept_id = c.dept_id
FROM client c
WHERE p.dept_id IS NULL
  AND p.client_id = c.id;

ALTER TABLE project ALTER COLUMN dept_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_project_dept_id ON project (dept_id);

DROP INDEX IF EXISTS idx_client_dept_id;
ALTER TABLE client DROP COLUMN IF EXISTS dept_id;
