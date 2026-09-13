-- V66: 对公付款模块（登记→审批→付款，归集项目计入成本）+ 菜单权限
CREATE TABLE IF NOT EXISTS vendor_payment (
    id            BIGSERIAL PRIMARY KEY,
    payment_no    VARCHAR(30) NOT NULL UNIQUE,
    vendor_name   VARCHAR(200) NOT NULL,
    summary       VARCHAR(500),
    project_id    BIGINT,
    contract_id   BIGINT,
    amount        NUMERIC(14,2) NOT NULL,
    tax_rate      NUMERIC(5,2),
    tax_amount    NUMERIC(14,2),
    amount_ex_tax NUMERIC(14,2),
    payment_date  DATE,
    payment_method VARCHAR(20),
    invoice_no    VARCHAR(50),
    status        SMALLINT NOT NULL DEFAULT 0,
    approver_name VARCHAR(50),
    approve_comment VARCHAR(300),
    approve_time  TIMESTAMP,
    paid_by       VARCHAR(50),
    paid_time     TIMESTAMP,
    create_by     VARCHAR(50),
    creator_name  VARCHAR(50),
    create_time   TIMESTAMP DEFAULT NOW(),
    update_by     VARCHAR(50),
    update_time   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_vendor_payment_project ON vendor_payment(project_id);

CREATE TABLE IF NOT EXISTS vendor_payment_attachment (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  BIGINT NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_size   BIGINT,
    content_type VARCHAR(100),
    create_by   VARCHAR(50),
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vendor_payment_att ON vendor_payment_attachment(payment_id);

-- 菜单：业务管理(100) 下"对公付款"
INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort)
SELECT 200, 100, '对公付款', '/business/vendor', 'business/vendor/index', 'business:vendor:list', 'Money', 1, 65
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 200);

INSERT INTO sys_menu (id, parent_id, name, perm, type, sort)
SELECT 201, 200, '付款登记', 'business:vendor:add', 2, 10
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 201);
INSERT INTO sys_menu (id, parent_id, name, perm, type, sort)
SELECT 202, 200, '付款编辑', 'business:vendor:edit', 2, 20
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 202);
INSERT INTO sys_menu (id, parent_id, name, perm, type, sort)
SELECT 203, 200, '付款删除', 'business:vendor:delete', 2, 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 203);
INSERT INTO sys_menu (id, parent_id, name, perm, type, sort)
SELECT 204, 200, '付款审批', 'business:vendor:approve', 2, 40
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 204);

-- 角色：管理员/经理/财务 全部；普通员工 可看可登记可编辑（审批仅经理及以上）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM (VALUES (1),(2),(5)) AS r(role_id), (VALUES (200),(201),(202),(203),(204)) AS m(menu_id)
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = r.role_id AND menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM (VALUES (200),(201),(202),(203)) AS m(menu_id)
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id = m.menu_id);
