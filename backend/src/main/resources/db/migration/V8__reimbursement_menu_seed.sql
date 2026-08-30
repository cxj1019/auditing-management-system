-- =====================================================
-- V8: 报销管理菜单与角色授权种子数据（菜单 ID 使用 120 段）
-- =====================================================

-- 报销管理菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(120, 100, '报销管理', '/business/reimbursement', 'business/reimbursement/index', 'business:reimbursement:list', 'Tickets', 1, 3, NOW());

-- 报销管理按钮权限点
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(121, 120, '报销提交', NULL, NULL, 'business:reimbursement:add', NULL, 2, 1, NOW()),
(122, 120, '报销编辑', NULL, NULL, 'business:reimbursement:edit', NULL, 2, 2, NOW()),
(123, 120, '报销删除', NULL, NULL, 'business:reimbursement:delete', NULL, 2, 3, NOW()),
(124, 120, '报销审批', NULL, NULL, 'business:reimbursement:approve', NULL, 2, 4, NOW());

-- 角色授权：系统管理员全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 120), (1, 121), (1, 122), (1, 123), (1, 124);

-- 角色授权：项目经理全量（含审批）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 120), (2, 121), (2, 122), (2, 123), (2, 124);

-- 角色授权：普通员工可提交与维护自己的单据，无审批权
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 120), (3, 121), (3, 122), (3, 123);

-- 推进菜单表自增序列到当前最大值
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
