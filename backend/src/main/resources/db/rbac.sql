-- 密码字段请按需执行：ALTER TABLE sys_user ADD COLUMN password VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 密码哈希' AFTER phone;

CREATE TABLE IF NOT EXISTS sys_role ( -- 若表不存在则创建角色表
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键', -- 声明自增主键字段
    code       VARCHAR(64)  NOT NULL COMMENT '角色编码', -- 声明角色编码字段
    name       VARCHAR(64)  NOT NULL COMMENT '角色名称', -- 声明角色名称字段
    created_at DATETIME(6)  NOT NULL COMMENT '创建时间', -- 声明创建时间字段
    updated_at DATETIME(6)  NOT NULL COMMENT '更新时间', -- 声明更新时间字段
    PRIMARY KEY (id), -- 将 id 设置为主键
    UNIQUE KEY uk_sys_role_code (code) -- 为角色编码添加唯一索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色'; -- 使用 InnoDB 与 utf8mb4 完成建表

CREATE TABLE IF NOT EXISTS sys_permission ( -- 若表不存在则创建权限表
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键', -- 声明自增主键字段
    code       VARCHAR(64)  NOT NULL COMMENT '权限编码', -- 声明权限编码字段
    name       VARCHAR(64)  NOT NULL COMMENT '权限名称', -- 声明权限名称字段
    created_at DATETIME(6)  NOT NULL COMMENT '创建时间', -- 声明创建时间字段
    updated_at DATETIME(6)  NOT NULL COMMENT '更新时间', -- 声明更新时间字段
    PRIMARY KEY (id), -- 将 id 设置为主键
    UNIQUE KEY uk_sys_permission_code (code) -- 为权限编码添加唯一索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限'; -- 使用 InnoDB 与 utf8mb4 完成建表

CREATE TABLE IF NOT EXISTS sys_user_role ( -- 若表不存在则创建用户角色关联表
    user_id BIGINT NOT NULL COMMENT '用户ID', -- 声明用户外键字段
    role_id BIGINT NOT NULL COMMENT '角色ID', -- 声明角色外键字段
    PRIMARY KEY (user_id, role_id) -- 用联合主键避免重复授权
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色'; -- 使用 InnoDB 与 utf8mb4 完成建表

CREATE TABLE IF NOT EXISTS sys_role_permission ( -- 若表不存在则创建角色权限关联表
    role_id       BIGINT NOT NULL COMMENT '角色ID', -- 声明角色外键字段
    permission_id BIGINT NOT NULL COMMENT '权限ID', -- 声明权限外键字段
    PRIMARY KEY (role_id, permission_id) -- 用联合主键避免重复授权
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限'; -- 使用 InnoDB 与 utf8mb4 完成建表

INSERT IGNORE INTO sys_role (id, code, name, created_at, updated_at) VALUES -- 写入内置角色并忽略已存在记录
(1, 'ADMIN', '管理员', NOW(6), NOW(6)), -- 插入管理员角色
(2, 'USER', '普通用户', NOW(6), NOW(6)); -- 插入普通用户角色

INSERT IGNORE INTO sys_permission (id, code, name, created_at, updated_at) VALUES -- 写入内置权限并忽略已存在记录
(1, 'dashboard:view', '查看工作台', NOW(6), NOW(6)), -- 插入工作台查看权限
(2, 'dataset:read', '查看数据集', NOW(6), NOW(6)), -- 插入数据集查看权限
(3, 'dataset:write', '管理数据集', NOW(6), NOW(6)), -- 插入数据集写权限
(4, 'task:read', '查看任务', NOW(6), NOW(6)), -- 插入任务查看权限
(5, 'task:write', '管理任务', NOW(6), NOW(6)), -- 插入任务写权限
(6, 'result:read', '查看结果', NOW(6), NOW(6)), -- 插入结果查看权限
(7, 'user:read', '查看用户', NOW(6), NOW(6)), -- 插入用户查看权限
(8, 'user:write', '管理用户', NOW(6), NOW(6)); -- 插入用户写权限

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES -- 给管理员授予全部权限
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8); -- 管理员拥有 1 到 8 号权限

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES -- 给普通用户授予业务权限
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6); -- 普通用户没有用户管理权限
