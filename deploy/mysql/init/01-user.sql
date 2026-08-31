CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(64)  NOT NULL COMMENT '登录名',
    nickname   VARCHAR(64)  NOT NULL COMMENT '显示名',
    email      VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone      VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    password   VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 密码哈希',
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
    created_at DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';
