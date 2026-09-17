INSERT IGNORE INTO sys_menu (id, parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at) VALUES
(8, 0, 'GIS 标注', '/gis', 'Location', 50, 'gis:read', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(9, 8, '编辑 GIS 标注', NULL, NULL, 51, 'gis:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 8), (1, 9);

INSERT INTO sys_menu (parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at)
SELECT 0, '摄像头列表', '/cameras', 'VideoCamera', 52, 'camera:read', 'MENU', 'ENABLED', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'camera:read');

INSERT INTO sys_menu (parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at)
SELECT id, '编辑摄像头', NULL, NULL, 53, 'camera:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6)
FROM sys_menu
WHERE permission_code = 'camera:read'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'camera:write');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE permission_code IN ('camera:read', 'camera:write');

INSERT INTO sys_menu (parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at)
SELECT 0, '官方灾害预警', '/official-alerts', 'Warning', 54, 'official-alert:read', 'MENU', 'ENABLED', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'official-alert:read');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE permission_code = 'official-alert:read';
