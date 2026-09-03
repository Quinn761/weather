package com.weatherhub.ai.kb.dto;

import java.time.LocalDateTime;

public record KnowledgeArticleVO(
        Long id,
        String title,
        String content,
        String tags,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
