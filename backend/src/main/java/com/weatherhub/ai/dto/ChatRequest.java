package com.weatherhub.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "请输入问题") String message,
        List<ChatTurn> history
) {
}
