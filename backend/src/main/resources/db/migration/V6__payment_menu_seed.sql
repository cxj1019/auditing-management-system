-- =====================================================
-- V6: 收款管理菜单与角色授权种子数据（菜单 ID 使用 110 段）
-- 业务管理目录(id=100)已在 V4 授权给三个角色，此处仅授权收款菜单
-- =====================================================

-- 收款管理菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(110, 100, '收款管理', '/business/collection', 'business/collection/index', 'business:collection:list', 'Money', 1, 2, NOW());

-- 收款管理按钮权限点
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(111, 110, '收款登记', NULL, NULL, 'business:collection:add', NULL, 2, 1, NOW()),
(112, 110, '收款编辑', NULL, NULL, 'business:collection:edit', NULL, 2, 2, NOW()),
(113, 110, '收款删除', NULL, NULL, 'business:collection:delete', NULL, 2, 3, NOW());

-- 角色授权：系统管理员全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 110), (1, 111), (1, 112), (1, 113);

-- 角色授权：项目经理全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 110), (2, 111), (2, 112), (2, 113);

-- 角色授权：普通员工仅查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 110);

-- 推进菜单表自增序列到当前最大值
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
