-- V24: 客户管理菜单权限
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(160, 100, '客户管理', '/business/client', 'business/client/index', 'business:client:list', 'Avatar', 1, 6, NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(161, 160, '客户登记', NULL, NULL, 'business:client:add', NULL, 2, 1, NOW()),
(162, 160, '客户编辑', NULL, NULL, 'business:client:edit', NULL, 2, 2, NOW()),
(163, 160, '客户删除', NULL, NULL, 'business:client:delete', NULL, 2, 3, NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (160, 161, 162, 163);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id IN (160, 161, 162, 163);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (160, 161, 162, 163);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (3, 160);

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
