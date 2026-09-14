package com.weatherhub.ai.tool;

import com.weatherhub.user.UserService;
import com.weatherhub.rbac.RoleService;
import com.weatherhub.rbac.MenuService;
import com.weatherhub.ai.kb.KnowledgeBaseService;
import com.weatherhub.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class SystemDataTool implements AiTool {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final UserService users;
    private final RoleService roles;
    private final MenuService menus;
    private final KnowledgeBaseService knowledge;
    private final AiProperties ai;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public SystemDataTool(UserService users, RoleService roles, MenuService menus,
                          KnowledgeBaseService knowledge, AiProperties ai, JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.users = users; this.roles = roles; this.menus = menus; this.knowledge = knowledge;
        this.ai = ai; this.jdbc = jdbc; this.redis = redis;
    }
    public String name() { return "get_system_data"; }
    public String description() {
        return "读取当前系统实际数据：users 用户状态/角色/权限（不含联系方式和密码），roles 角色及菜单绑定，menus 菜单树及权限编码，knowledge 知识文章元数据，runtime 当前后端配置和数据库连通性。每次只查询一个 scope，可按用户关键词和页码查询。不能查询远程部署或日志。";
    }
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "required", List.of("scope"), "properties", Map.of(
                "scope", Map.of("type", "string", "enum", List.of("users", "roles", "menus", "knowledge", "runtime")),
                "keyword", Map.of("type", "string", "description", "用户名、昵称或知识标题关键词，不要填整个问句"),
                "page", Map.of("type", "integer", "minimum", 1, "description", "用户页码，每页最多20条")));
    }
    public String execute(JsonNode arguments) {
        String scope = text(arguments, "scope");
        String permission = switch (scope) {
            case "users" -> "user:read";
            case "roles" -> "role:read";
            case "menus" -> "menu:read";
            case "knowledge" -> "kb:read";
            case "runtime" -> "dashboard:view";
            default -> "";
        };
        if (permission.isEmpty()) return "请选择有效 scope：users、roles、menus、knowledge、runtime。";
        if (!ToolAccess.allowed(permission)) return "没有访问权限：" + permission + "。未读取该数据。";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "当前后端实时查询 / " + scope);
        result.put("queriedAt", Instant.now().toString());
        try {
            switch (scope) {
                case "users" -> {
                    long page = 1;
                    try { page = Math.max(1, Math.min(10000, Long.parseLong(text(arguments, "page")))); }
                    catch (NumberFormatException ignored) {}
                    var found = users.page(page, 20, text(arguments, "keyword"));
                    result.put("total", found.getTotal());
                    result.put("page", found.getCurrent());
                    result.put("pageSize", 20);
                    result.put("records", found.getRecords().stream().map(user -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", user.id()); row.put("username", user.username());
                        row.put("nickname", user.nickname()); row.put("status", user.status());
                        row.put("roles", user.roles()); row.put("roleIds", user.roleIds());
                        row.put("permissions", user.permissions());
                        return row;
                    }).toList());
                }
                case "roles" -> result.put("records", roles.list());
                case "menus" -> result.put("records", menus.tree());
                case "knowledge" -> {
                    var articles = knowledge.list(text(arguments, "keyword"));
                    result.put("total", articles.size());
                    result.put("limit", 50);
                    result.put("records", articles.stream().limit(50).map(article -> Map.of(
                            "id", article.id(), "title", article.title(), "status", article.status(),
                            "updatedAt", String.valueOf(article.updatedAt()))).toList());
                }
                case "runtime" -> {
                    result.put("aiEnabled", ai.isEnabled());
                    result.put("javaModelConfigured", ai.hasApiKey());
                    result.put("model", ai.getModel());
                    result.put("embeddingConfigured", ai.hasEmbeddingModel());
                    result.put("pythonAgentConfigured", ai.hasPythonAgentUrl());
                    result.put("mysql", databaseStatus()); result.put("redis", redisStatus());
                    result.put("limitations", "仅本后端配置与连通性；未探测模型供应商、Python、SAM推理、PostGIS、远程服务器或Kubernetes。configured不等于服务可用。");
                }
            }
        } catch (Exception ex) {
            result.put("error", "查询失败，当前数据未知，请检查对应服务。不能据此推断记录为零。");
        }
        return JSON.writeValueAsString(result);
    }
    private String databaseStatus() {
        try { return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class)) ? "UP" : "UNKNOWN"; }
        catch (Exception ex) { return "UNAVAILABLE"; }
    }
    private String redisStatus() {
        try (var connection = redis.getConnectionFactory().getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "UNKNOWN";
        } catch (Exception ex) { return "UNAVAILABLE"; }
    }
    private static String text(JsonNode args, String field) {
        return args == null || args.get(field) == null || args.get(field).isNull() ? "" : args.get(field).asString().trim();
    }
}
