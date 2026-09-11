package com.weatherhub.ai.dto;

public record AgentStreamEvent(
        String type,
        String text,
        AgentRunVO run,
        String message
) {
    public static AgentStreamEvent delta(String text) {
        return new AgentStreamEvent("delta", text, null, null);
    }

    public static AgentStreamEvent end() {
        return new AgentStreamEvent("end", null, null, null);
    }

    public static AgentStreamEvent done(AgentRunVO run) {
        return new AgentStreamEvent("done", null, run, null);
    }

    public static AgentStreamEvent error(String message) {
        return new AgentStreamEvent("error", null, null, message);
    }
}
