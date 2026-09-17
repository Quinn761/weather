CREATE TABLE IF NOT EXISTS sys_menu (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id        BIGINT       NOT NULL DEFAULT 0,
    name             VARCHAR(64)  NOT NULL,
    path             VARCHAR(128) DEFAULT NULL,
    icon             VARCHAR(64)  DEFAULT NULL,
    sort_no          INT          NOT NULL DEFAULT 0,
    permission_code  VARCHAR(64)  DEFAULT NULL,
    type             VARCHAR(16)  NOT NULL DEFAULT 'MENU',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sys_menu (id, parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at) VALUES
(1, 0, '工作台', '/dashboard', 'Odometer', 10, 'dashboard:view', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(2, 0, '用户管理', '/users', 'User', 20, 'user:read', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(3, 0, '角色管理', '/roles', 'Avatar', 30, 'role:read', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(4, 0, '菜单管理', '/menus', 'Menu', 40, 'menu:read', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(5, 2, '编辑用户', NULL, NULL, 21, 'user:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6)),
(6, 3, '编辑角色', NULL, NULL, 31, 'role:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6)),
(7, 4, '编辑菜单', NULL, NULL, 41, 'menu:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6)),
(8, 0, 'GIS 标注', '/gis', 'Location', 50, 'gis:read', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(9, 8, '编辑 GIS 标注', NULL, NULL, 51, 'gis:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9);

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

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(2, 1);
