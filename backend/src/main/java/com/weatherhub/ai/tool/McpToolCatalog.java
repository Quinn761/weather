package com.weatherhub.ai.tool;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpToolCatalog {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, AiTool> tools;

    public McpToolCatalog(List<AiTool> tools) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            map.put(tool.name(), tool);
        }
        this.tools = Map.copyOf(map);
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public List<Map<String, Object>> openAiTools() {
        return tools.values().stream().map(tool -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.inputSchema());
            return Map.of("type", "function", "function", function);
        }).toList();
    }

    public List<Map<String, Object>> mcpTools() {
        return tools.values().stream().map(tool -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("description", tool.description());
            item.put("inputSchema", tool.inputSchema());
            return item;
        }).toList();
    }

    public String call(String name, JsonNode arguments) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            return "未知工具：" + name;
        }
        String permission = switch (name) {
            case "get_dashboard_overview" -> "dashboard:view";
            case "list_gis_features" -> "gis:read";
            default -> "";
        };
        if (!permission.isEmpty() && !ToolAccess.allowed(permission)) {
            return "没有访问权限：" + permission + "。未读取该数据。";
        }
        JsonNode args = arguments == null || arguments.isNull() ? JSON.readTree("{}") : arguments;
        try {
            return tool.execute(args);
        } catch (Exception ex) {
            return "工具执行失败：" + ex.getMessage();
        }
    }

    public String call(String name, String argumentsJson) {
        JsonNode node;
        try {
            node = JSON.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (Exception ex) {
            node = JSON.readTree("{}");
        }
        return call(name, node);
    }
}
