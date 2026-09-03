package com.weatherhub.ai.tool;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolCatalogTest {

    @Test
    void listsAndCallsRegisteredTool() {
        AiTool echo = new AiTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "回声";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public String execute(JsonNode arguments) {
                return "pong";
            }
        };
        McpToolCatalog catalog = new McpToolCatalog(List.of(echo));
        assertEquals(List.of("echo"), catalog.names());
        assertTrue(catalog.openAiTools().getFirst().get("type").equals("function"));
        assertEquals("pong", catalog.call("echo", "{}"));
        JsonMapper json = JsonMapper.builder().build();
        assertEquals("pong", catalog.call("echo", json.readTree("{}")));
        assertTrue(catalog.call("missing", "{}").contains("未知工具"));
    }
}
