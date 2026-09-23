package com.weatherhub.ai.agent;

/** A classification result. It contains no tool name, permission, or action. */
public record IntentDecision(String intent, String source, double confidence) {
}
