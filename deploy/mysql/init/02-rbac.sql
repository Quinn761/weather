CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(64)  NOT NULL COMMENT '角色编码',
    name       VARCHAR(64)  NOT NULL COMMENT '角色名称',
    created_at DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS sys_permission (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(64)  NOT NULL COMMENT '权限编码',
    name       VARCHAR(64)  NOT NULL COMMENT '权限名称',
    created_at DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       BIGINT NOT NULL COMMENT '角色外键',
    permission_id BIGINT NOT NULL COMMENT '权限外键',
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限';

INSERT IGNORE INTO sys_role (id, code, name, created_at, updated_at) VALUES
(1, 'ADMIN', '管理员', NOW(6), NOW(6)),
(2, 'USER', '普通用户', NOW(6), NOW(6));
