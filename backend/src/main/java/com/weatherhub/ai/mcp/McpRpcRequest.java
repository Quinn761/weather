package com.weatherhub.ai.mcp;

import java.util.Map;

public record McpRpcRequest(String jsonrpc, Object id, String method, Map<String, Object> params) {
}
