-- V61: 财务角色补充"首页"菜单
-- 测试发现财务角色未配置首页菜单（id=5），导致财务账号桌面端登录后落到 /dashboard 404
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 5
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 5 AND menu_id = 5);
