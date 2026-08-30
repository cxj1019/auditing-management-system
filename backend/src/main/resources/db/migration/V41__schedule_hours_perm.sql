-- V41: 工时统计权限（仅管理员/项目经理；普通员工不展示工时）
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(174, 170, '工时统计', NULL, NULL, 'business:schedule:hours', NULL, 2, 4, NOW());

-- 角色授权：系统管理员、项目经理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 174), (2, 174);

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
