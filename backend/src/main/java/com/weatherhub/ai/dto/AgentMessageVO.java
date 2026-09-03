package com.weatherhub.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentMessageVO(
        Long id,
        String role,
        String content,
        String agent,
        LocalDateTime createdAt,
        String mode,
        List<AgentTaskVO> plan,
        List<String> usedTools,
        List<String> ragSources,
        List<TraceStep> trace
) {
}
