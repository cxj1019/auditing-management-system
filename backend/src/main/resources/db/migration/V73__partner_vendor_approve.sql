-- V73: 合伙人补对公付款权限（模块可见 + 审批）
-- V66 授予对公付款菜单(200~204)时漏了合伙人(role 4)，此处补齐：
--   200 对公付款列表(business:vendor:list) —— 否则连模块入口都没有
--   204 付款审批(business:vendor:approve)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, m.menu_id FROM (VALUES (200), (204)) AS m(menu_id)
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 4 AND menu_id = m.menu_id);
