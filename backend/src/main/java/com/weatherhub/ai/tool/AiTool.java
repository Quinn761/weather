package com.weatherhub.ai.tool;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public interface AiTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    String execute(JsonNode arguments);
}
