-- =====================================================
-- V2: 初始化数据（PostgreSQL / Supabase）
-- 默认管理员账号 + 三种角色模板 + 菜单权限数据
-- 管理员账号：admin / admin123（BCrypt 加密存储）
-- =====================================================

-- ---------- 用户 ----------
INSERT INTO sys_user (id, username, password, nickname, status, create_by, create_time)
VALUES (1, 'admin', '$2a$10$QqAtw2EoPRT6mSh.ZXeBeeE/OR3kvto.g/WgZs6PCc24eOByVjRoi', '系统管理员', 1, 'system', NOW());

-- ---------- 角色 ----------
INSERT INTO sys_role (id, role_code, role_name, description, status, create_by, create_time) VALUES
(1, 'admin',    '系统管理员', '拥有系统全部权限',                 1, 'system', NOW()),
(2, 'manager',  '项目经理',   '业务模块管理权限（模块接入后扩展）', 1, 'system', NOW()),
(3, 'employee', '普通员工',   '基础访问权限（模块接入后扩展）',     1, 'system', NOW());

-- ---------- 菜单 ----------
-- 首页
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(5, 0, '首页', '/dashboard', 'dashboard/index', 'dashboard:view', 'HomeFilled', 1, 0, NOW());

-- 系统管理目录
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(1, 0, '系统管理', '/system', NULL, NULL, 'Setting', 0, 1, NOW());

-- 用户管理
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(2, 1, '用户管理', '/system/user', 'system/user/index', 'system:user:list', 'User', 1, 1, NOW()),
(21, 2, '用户新增', NULL, NULL, 'system:user:add', NULL, 2, 1, NOW()),
(22, 2, '用户编辑', NULL, NULL, 'system:user:edit', NULL, 2, 2, NOW()),
(23, 2, '用户删除', NULL, NULL, 'system:user:delete', NULL, 2, 3, NOW());

-- 角色管理
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(3, 1, '角色管理', '/system/role', 'system/role/index', 'system:role:list', 'UserFilled', 1, 2, NOW()),
(31, 3, '角色新增', NULL, NULL, 'system:role:add', NULL, 2, 1, NOW()),
(32, 3, '角色编辑', NULL, NULL, 'system:role:edit', NULL, 2, 2, NOW()),
(33, 3, '角色删除', NULL, NULL, 'system:role:delete', NULL, 2, 3, NOW());

-- 菜单管理
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(4, 1, '菜单管理', '/system/menu', 'system/menu/index', 'system:menu:list', 'Menu', 1, 3, NOW()),
(41, 4, '菜单新增', NULL, NULL, 'system:menu:add', NULL, 2, 1, NOW()),
(42, 4, '菜单编辑', NULL, NULL, 'system:menu:edit', NULL, 2, 2, NOW()),
(43, 4, '菜单删除', NULL, NULL, 'system:menu:delete', NULL, 2, 3, NOW());

-- ---------- 用户角色关联 ----------
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- ---------- 角色菜单关联 ----------
-- 系统管理员：全部菜单与按钮权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
(1, 21), (1, 22), (1, 23),
(1, 31), (1, 32), (1, 33),
(1, 41), (1, 42), (1, 43);

-- 项目经理：首页（业务模块接入后按需扩展）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 5);

-- 普通员工：首页（业务模块接入后按需扩展）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (3, 5);

-- ---------- 重置自增序列 ----------
-- 种子数据使用了显式主键，需将各表 IDENTITY 序列推进到当前最大值，
-- 否则后续自动插入会与已有主键冲突
SELECT setval(pg_get_serial_sequence('sys_user', 'id'),      (SELECT MAX(id) FROM sys_user));
SELECT setval(pg_get_serial_sequence('sys_role', 'id'),      (SELECT MAX(id) FROM sys_role));
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'),      (SELECT MAX(id) FROM sys_menu));
SELECT setval(pg_get_serial_sequence('sys_user_role', 'id'), (SELECT MAX(id) FROM sys_user_role));
SELECT setval(pg_get_serial_sequence('sys_role_menu', 'id'), (SELECT MAX(id) FROM sys_role_menu));
