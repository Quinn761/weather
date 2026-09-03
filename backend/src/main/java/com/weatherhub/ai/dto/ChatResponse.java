package com.weatherhub.ai.dto;

import java.util.List;

public record ChatResponse(
        String reply,
        String model,
        boolean configured,
        List<TraceStep> trace,
        List<String> usedTools,
        List<String> ragSources
) {
}
