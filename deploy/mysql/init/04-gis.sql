CREATE TABLE IF NOT EXISTS gis_feature (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    geojson     JSON         NOT NULL,
    properties  JSON         NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sys_menu (id, parent_id, name, path, icon, sort_no, permission_code, type, status, created_at, updated_at) VALUES
(8, 0, 'GIS 标注', '/gis', 'Location', 50, 'gis:read', 'MENU', 'ENABLED', NOW(6), NOW(6)),
(9, 8, '编辑 GIS 标注', NULL, NULL, 51, 'gis:write', 'BUTTON', 'ENABLED', NOW(6), NOW(6));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 8), (1, 9);
