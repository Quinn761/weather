package com.weatherhub.ai.agent;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/** The workflow layer owns the deterministic intent-to-tool mapping. */
@Service
public class AgentWorkflowEngine {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public List<PlanStep> build(IntentDecision decision, String question) {
        PlanStep intent = new PlanStep("intent", "意图层 · " + decision.source() + " 分类为 " + decision.intent(), null);
        PlanStep reviewer = new PlanStep("reviewer", "质检官汇总已授权的证据并回答", null);
        return switch (decision.intent()) {
            case "weather" -> List.of(intent, new PlanStep("operator", "工作流 · 查询实时天气", "get_current_weather", json(Map.of("query", question))), reviewer);
            case "dashboard" -> List.of(intent, new PlanStep("operator", "工作流 · 查询工作台概览", "get_dashboard_overview", "{}"), reviewer);
            case "gis" -> List.of(intent, new PlanStep("operator", "工作流 · 查询 GIS 标注", "list_gis_features", "{}"), reviewer);
            case "current_user" -> List.of(intent, new PlanStep("operator", "工作流 · 查询当前登录用户", "get_current_user", "{}"), reviewer);
            case "system_knowledge" -> List.of(intent, new PlanStep("knowledge", "工作流 · 检索项目知识", "search_knowledge", json(Map.of("query", question))), reviewer);
            case "official_alert" -> List.of(intent, new PlanStep("operator", "Official alert lookup", "get_official_alerts", json(Map.of("query", question))), reviewer);
            default -> List.of(intent, reviewer);
        };
    }

    private static String json(Map<String, Object> value) {
        return JSON.writeValueAsString(value);
    }
}
