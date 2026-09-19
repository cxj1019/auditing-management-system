-- V68: AI 设置（OpenAI 兼容接口，管理员配置）+ AI 设置菜单
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   VARCHAR(50) PRIMARY KEY,
    setting_value TEXT,
    update_by     VARCHAR(50),
    update_time   TIMESTAMP DEFAULT NOW()
);

INSERT INTO sys_menu (id, parent_id, name, path, component, perm, icon, type, sort, visible)
SELECT 205, 1, 'AI 设置', '/system/ai', 'system/ai/index', 'system:ai:list', 'MagicStick', 1, 70, b'0'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 205);

-- 仅管理员可见可用
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 205 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 205);
