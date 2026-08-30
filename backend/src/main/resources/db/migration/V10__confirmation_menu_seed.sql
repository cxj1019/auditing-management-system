-- =====================================================
-- V10: 函证管理菜单与角色授权种子数据（菜单 ID 使用 130 段）
-- =====================================================

-- 函证管理菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(130, 100, '函证管理', '/business/confirmation', 'business/confirmation/index', 'business:confirmation:list', 'Postcard', 1, 4, NOW());

-- 函证管理按钮权限点
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(131, 130, '函证登记', NULL, NULL, 'business:confirmation:add', NULL, 2, 1, NOW()),
(132, 130, '函证编辑', NULL, NULL, 'business:confirmation:edit', NULL, 2, 2, NOW()),
(133, 130, '函证删除', NULL, NULL, 'business:confirmation:delete', NULL, 2, 3, NOW()),
(134, 130, '函证流转', NULL, NULL, 'business:confirmation:status', NULL, 2, 4, NOW());

-- 角色授权：系统管理员全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 130), (1, 131), (1, 132), (1, 133), (1, 134);

-- 角色授权：项目经理全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 130), (2, 131), (2, 132), (2, 133), (2, 134);

-- 角色授权：普通员工仅查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 130);

-- 推进菜单表自增序列到当前最大值
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
