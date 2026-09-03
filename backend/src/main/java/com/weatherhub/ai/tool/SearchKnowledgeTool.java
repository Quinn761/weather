package com.weatherhub.ai.tool;

import com.weatherhub.ai.rag.RagHit;
import com.weatherhub.ai.rag.RagService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchKnowledgeTool implements AiTool {

    private final RagService ragService;

    public SearchKnowledgeTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "从知识库检索说明文档。问原理、怎么实现、系统约定时可以调用。知识库内容可在「知识库」页面维护。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "检索语句")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        String query = "";
        if (arguments != null && arguments.get("query") != null && !arguments.get("query").isNull()) {
            query = arguments.get("query").asString().trim();
        }
        if (query.isEmpty()) {
            return "请提供 query。";
        }
        List<RagHit> hits = ragService.retrieve(query, 3);
        if (hits.isEmpty()) {
            return "知识库没有与「" + query + "」匹配的条目。";
        }
        return hits.stream()
                .map(hit -> "《" + hit.title() + "》\n" + hit.content())
                .collect(Collectors.joining("\n\n"));
    }
}
