package com.weatherhub.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentRunRequest(
        Long sessionId,
        @NotBlank(message = "请输入任务目标") String message
) {
}
