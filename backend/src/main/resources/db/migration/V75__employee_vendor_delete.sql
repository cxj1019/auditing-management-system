-- V75: 恢复员工删除自己付款草稿的权限
-- V70 收紧员工权限时一并收走了 vendor:edit 与 vendor:delete，但登记人的草稿
-- 只有登记人能改（服务端"仅登记人可编辑"校验），导致写错的草稿成为死稿：
-- 员工不能改不能删，经理有权限却过不了归属校验。恢复 vendor:delete(菜单203)，
-- 服务端本就限定"仅草稿/已驳回 + 仅登记人本人"，风险可控。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, 203
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id = 203);
