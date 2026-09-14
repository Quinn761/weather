package com.weatherhub.ai.agent;

public record PlanStep(String agent, String title, String tool, String arguments) {
    public PlanStep(String agent, String title, String tool) {
        this(agent, title, tool, null);
    }
}
