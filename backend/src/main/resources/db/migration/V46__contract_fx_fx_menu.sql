-- V46: 合同外币金额支持 + 汇率牌价查询菜单

ALTER TABLE contract ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT '人民币';
ALTER TABLE contract ADD COLUMN foreign_amount NUMERIC(18,2);
ALTER TABLE contract ADD COLUMN exchange_rate NUMERIC(12,4);
ALTER TABLE contract ADD COLUMN rate_publish_time VARCHAR(40);

COMMENT ON COLUMN contract.currency IS '币种：人民币/美元/日元/欧元/港币/英镑';
COMMENT ON COLUMN contract.exchange_rate IS '中国银行牌价（每 100 外币兑人民币）';

-- 汇率牌价查询菜单（业务管理目录 id=100，196 段）
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, create_time) VALUES
(196, 100, '汇率牌价', '/business/fx', 'business/fx/index', 'business:fx:list', 'Coin', 1, 8, NOW());

-- 角色授权：全部角色可查
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.id FROM (VALUES (1), (2), (3)) AS r(role_id)
CROSS JOIN sys_menu m WHERE m.id = 196;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));
