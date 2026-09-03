package com.weatherhub.ai.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AgentSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_session (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_id BIGINT NOT NULL,
                        title VARCHAR(128) NOT NULL,
                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL,
                        KEY idx_ai_session_user (user_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_message (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        session_id BIGINT NOT NULL,
                        role VARCHAR(16) NOT NULL,
                        content TEXT NOT NULL,
                        agent VARCHAR(32) NULL,
                        payload TEXT NULL,
                        created_at DATETIME NOT NULL,
                        KEY idx_ai_message_session (session_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_memory (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_id BIGINT NOT NULL,
                        mem_key VARCHAR(64) NOT NULL,
                        mem_value TEXT NOT NULL,
                        updated_at DATETIME NOT NULL,
                        UNIQUE KEY uk_ai_memory (user_id, mem_key)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_knowledge (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        title VARCHAR(128) NOT NULL,
                        content TEXT NOT NULL,
                        tags VARCHAR(255) NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL,
                        KEY idx_ai_knowledge_status (status)
                    )
                    """);
            seedKnowledge();
        } catch (Exception ex) {
            log.warn("初始化 Agent 表失败：{}", ex.getMessage());
        }
    }

    private void seedKnowledge() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_knowledge", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        insertKnowledge("RBAC 权限模型",
                "用户-角色-菜单多对多。表：sys_user、sys_role、sys_menu、sys_user_role、sys_role_menu。"
                        + "权限编码写在菜单 permission_code，例如 user:read、gis:write。"
                        + "JwtAuthFilter 调用 MenuMapper.selectCodesByUserId，转成 SimpleGrantedAuthority。"
                        + "SecurityConfig 用 hasAuthority 控接口，不用 hasRole。侧栏只展示 TYPE_MENU/DIR。",
                "rbac,权限", now);
        insertKnowledge("JWT 与退出黑名单",
                "登录 AuthService 校验 BCrypt 后由 JwtService 签发 HS256，subject 是 userId，默认 24 小时。"
                        + "POST /api/auth/logout 把 SHA-256(token) 写入 Redis 键 auth:deny:，TTL 等于剩余有效期。"
                        + "JwtAuthFilter 发现黑名单则不写入 SecurityContext。Redis 故障时黑名单 fail-open。",
                "jwt,redis", now);
        insertKnowledge("GIS 标注",
                "独立 PostGIS 库 weatherhub_gis，表 gis_feature。"
                        + "写入：ST_SetSRID(ST_Force3D(ST_GeomFromGeoJSON(...)), 4326)，GiST 索引。"
                        + "前端 Cesium 打点/圈地。读接口要 gis:read，写要 gis:write。"
                        + "问当前有哪些标注时必须调用 list_gis_features，不要编造。",
                "gis,postgis", now);
        insertKnowledge("双数据源",
                "GisDataSourceConfig 两个 Hikari 池：weatherhub-mysql 标 @Primary，weatherhub-postgis 独立。"
                        + "GIS 事务必须 @Transactional(\"gisTransactionManager\")。"
                        + "com.weatherhub.gis 的 Mapper 绑定 gisSqlSessionFactory。",
                "mysql,postgis", now);
        insertKnowledge("AI 助手链路",
                "调用顺序：Prompt 组装系统提示词 → RAG 检索知识库 → 调大模型 API → 若返回 tool_calls 则走 Function Calling"
                        + " → 工具来自 MCP 工具目录（与 HTTP JSON-RPC /api/ai/mcp 同一套）→ Agent 循环直到给出最终回答。"
                        + "兼容 OpenAI 协议，默认对接 DeepSeek，也可改接通义 compatible-mode。"
                        + "知识库页面维护的条目会进入 RAG，Agent 知识官会检索这些文档。",
                "ai,rag,agent", now);
    }

    private void insertKnowledge(String title, String content, String tags, LocalDateTime now) {
        jdbcTemplate.update(
                "INSERT INTO ai_knowledge (title, content, tags, status, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', ?, ?)",
                title, content, tags, now, now
        );
    }
}
