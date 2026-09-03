package com.weatherhub.ai.dto;

import java.time.LocalDateTime;

public record AgentSessionVO(Long id, String title, LocalDateTime updatedAt) {
}
