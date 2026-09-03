package com.weatherhub.ai.mcp;

import com.weatherhub.ai.tool.McpToolCatalog;
import com.weatherhub.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class McpController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final McpToolCatalog catalog;

    public McpController(McpToolCatalog catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/mcp")
    public ApiResponse<Map<String, Object>> handle(@RequestBody McpRpcRequest request) {
        String method = request.method() == null ? "" : request.method();
        Object result = switch (method) {
            case "initialize" -> Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "weather-data-hub", "version", "0.0.1")
            );
            case "ping", "notifications/initialized" -> Map.of("ok", true);
            case "tools/list" -> Map.of("tools", catalog.mcpTools());
            case "tools/call" -> callTool(request.params());
            default -> Map.of("error", "不支持的 MCP 方法：" + method);
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", request.jsonrpc() == null ? "2.0" : request.jsonrpc());
        body.put("id", request.id());
        body.put("result", result);
        return ApiResponse.ok(body);
    }

    private Map<String, Object> callTool(Map<String, Object> params) {
        if (params == null) {
            return Map.of("isError", true, "content", text("缺少 params"));
        }
        Object name = params.get("name");
        Object arguments = params.get("arguments");
        JsonNode argsNode = JSON.valueToTree(arguments == null ? Map.of() : arguments);
        String output = catalog.call(String.valueOf(name), argsNode);
        return Map.of(
                "isError", false,
                "content", text(output)
        );
    }

    private static Object text(String value) {
        return java.util.List.of(Map.of("type", "text", "text", value));
    }
}
