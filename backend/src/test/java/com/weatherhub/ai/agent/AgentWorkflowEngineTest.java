package com.weatherhub.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkflowEngineTest {
    private final AgentWorkflowEngine workflow = new AgentWorkflowEngine();

    @Test
    void intentAloneProducesTheFixedWorkflow() {
        var steps = workflow.build(new IntentDecision("gis", "jev", 0.99), "查看 GIS 标注");
        assertEquals("intent", steps.getFirst().agent());
        assertTrue(steps.stream().anyMatch(step -> "list_gis_features".equals(step.tool())));
    }

    @Test
    void chatIntentHasNoTool() {
        var steps = workflow.build(new IntentDecision("chat", "jev", 0.99), "写一首诗");
        assertTrue(steps.stream().noneMatch(step -> step.tool() != null));
    }

    @Test
    void officialAlertIntentUsesReadOnlyAlertTool() {
        var steps = workflow.build(new IntentDecision("official_alert", "jev", 0.99), "官方预警");
        assertTrue(steps.stream().anyMatch(step -> "get_official_alerts".equals(step.tool())));
    }
}
