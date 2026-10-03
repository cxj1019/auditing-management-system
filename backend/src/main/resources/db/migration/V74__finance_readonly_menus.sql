-- V74: 财务角色补业务模块只读权限
-- 数据层 DataScope 早已给财务全所视角(ALL)，但 V14/V16 等发放菜单时漏了财务，
-- 导致财务打开项目/合同/客户/发票/函证/成本分析直接 403，与复核岗位的实际需要不符。
-- 此处仅授予 6 个模块入口(type=1 列表菜单)，不含任何增删改/流转按钮。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, m.menu_id FROM (VALUES (101), (130), (140), (150), (160), (180)) AS m(menu_id)
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 5 AND menu_id = m.menu_id);
