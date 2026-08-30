-- V27: 日程管理菜单权限
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(170, 100, '日程管理', '/business/schedule', 'business/schedule/index', 'business:schedule:list', 'Calendar', 1, 7, NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(171, 170, '日程登记', NULL, NULL, 'business:schedule:add', NULL, 2, 1, NOW()),
(172, 170, '日程编辑', NULL, NULL, 'business:schedule:edit', NULL, 2, 2, NOW()),
(173, 170, '日程删除', NULL, NULL, 'business:schedule:delete', NULL, 2, 3, NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (170, 171, 172, 173);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id IN (170, 171, 172, 173);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id IN (170, 171, 172, 173);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (170, 171, 172, 173);

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
