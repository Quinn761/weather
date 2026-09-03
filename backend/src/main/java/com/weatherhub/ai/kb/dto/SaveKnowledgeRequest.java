package com.weatherhub.ai.kb.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveKnowledgeRequest(
        @NotBlank(message = "请填写标题") String title,
        @NotBlank(message = "请填写正文") String content,
        String tags,
        String status
) {
}
