package com.weatherhub.ai.tool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiToolConfig {

    @Bean
    public McpToolCatalog mcpToolCatalog(List<AiTool> tools) {
        return new McpToolCatalog(tools);
    }
}
