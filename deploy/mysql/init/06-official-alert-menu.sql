INSERT INTO sys_menu (parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at)
SELECT 0, '官方灾害预警', '/official-alerts', 'Warning', 54, 'official-alert:read', 'MENU', 'ENABLED', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'official-alert:read');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE permission_code = 'official-alert:read';
