-- =====================================================
-- V12: 成本分析菜单与角色授权种子数据（菜单 ID 使用 140 段）
-- =====================================================

-- 成本分析菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(140, 100, '成本分析', '/business/cost', 'business/cost/index', 'business:cost:list', 'DataAnalysis', 1, 5, NOW());

-- 人工成本按钮权限点
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(141, 140, '人工成本登记', NULL, NULL, 'business:cost:labor-add', NULL, 2, 1, NOW()),
(142, 140, '人工成本编辑', NULL, NULL, 'business:cost:labor-edit', NULL, 2, 2, NOW()),
(143, 140, '人工成本删除', NULL, NULL, 'business:cost:labor-delete', NULL, 2, 3, NOW());

-- 角色授权：系统管理员全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 140), (1, 141), (1, 142), (1, 143);

-- 角色授权：项目经理全量
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 140), (2, 141), (2, 142), (2, 143);

-- 角色授权：普通员工仅查看分析
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 140);

-- 推进菜单表自增序列到当前最大值
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
