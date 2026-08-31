CREATE TABLE IF NOT EXISTS sys_user ( -- 若表不存在则创建系统用户表
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键', -- 声明自增主键字段
    username   VARCHAR(64)  NOT NULL COMMENT '登录名', -- 声明登录名字段且不允许为空
    nickname   VARCHAR(64)  NOT NULL COMMENT '显示名', -- 声明显示名字段且不允许为空
    email      VARCHAR(128) DEFAULT NULL COMMENT '邮箱', -- 声明可选的邮箱字段
    phone      VARCHAR(32)  DEFAULT NULL COMMENT '手机号', -- 声明可选的手机号字段
    password   VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 密码哈希', -- 声明登录密码哈希字段
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED', -- 声明启用或停用状态字段
    created_at DATETIME(6)  NOT NULL COMMENT '创建时间', -- 声明创建时间字段
    updated_at DATETIME(6)  NOT NULL COMMENT '更新时间', -- 声明更新时间字段
    PRIMARY KEY (id), -- 将 id 设置为主键
    UNIQUE KEY uk_sys_user_username (username) -- 为用户名添加唯一索引避免重复
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户'; -- 使用 InnoDB 与 utf8mb4 完成建表
