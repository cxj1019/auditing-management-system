-- =====================================================
-- V14: 项目管理菜单与角色授权种子数据（菜单 ID 使用 150 段）
-- =====================================================

-- 项目管理菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(150, 100, '项目管理', '/business/project', 'business/project/index', 'business:project:list', 'Notebook', 1, 0, NOW());

-- 项目管理按钮权限点
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(151, 150, '项目登记', NULL, NULL, 'business:project:add', NULL, 2, 1, NOW()),
(152, 150, '项目编辑', NULL, NULL, 'business:project:edit', NULL, 2, 2, NOW()),
(153, 150, '项目删除', NULL, NULL, 'business:project:delete', NULL, 2, 3, NOW()),
(154, 150, '项目流转', NULL, NULL, 'business:project:status', NULL, 2, 4, NOW());

-- 角色授权：系统管理员全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 150), (1, 151), (1, 152), (1, 153), (1, 154);

-- 角色授权：项目经理全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 150), (2, 151), (2, 152), (2, 153), (2, 154);

-- 角色授权：普通员工仅查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 150);

-- 推进菜单表自增序列到当前最大值
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
