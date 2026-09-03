package com.weatherhub.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunVO(
        Long sessionId,
        String title,
        String reply,
        String mode,
        List<String> crew,
        List<AgentTaskVO> plan,
        List<String> usedTools,
        List<String> ragSources,
        List<TraceStep> trace,
        List<String> memories
) {
}
