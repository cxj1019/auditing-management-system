-- =====================================================
-- V4: 合同管理菜单与角色授权种子数据
-- 菜单 ID 使用 100 段（避开系统管理段 1-43）
-- =====================================================

-- 业务管理目录
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(100, 0, '业务管理', '/business', NULL, NULL, 'Briefcase', 0, 2, NOW());

-- 合同管理菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(101, 100, '合同管理', '/business/contract', 'business/contract/index', 'business:contract:list', 'Document', 1, 1, NOW());

-- 合同管理按钮权限点
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(102, 101, '合同新增', NULL, NULL, 'business:contract:add', NULL, 2, 1, NOW()),
(103, 101, '合同编辑', NULL, NULL, 'business:contract:edit', NULL, 2, 2, NOW()),
(104, 101, '合同删除', NULL, NULL, 'business:contract:delete', NULL, 2, 3, NOW()),
(105, 101, '合同状态流转', NULL, NULL, 'business:contract:status', NULL, 2, 4, NOW());

-- 角色授权：系统管理员全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 100), (1, 101), (1, 102), (1, 103), (1, 104), (1, 105);

-- 角色授权：项目经理全量（业务模块默认开放增删改查与流转）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 100), (2, 101), (2, 102), (2, 103), (2, 104), (2, 105);

-- 角色授权：普通员工仅查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 100), (3, 101);

-- 推进菜单表自增序列到当前最大值
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
