package com.weatherhub.ai.dto;

import java.util.List;

public record AiStatusVO(
        boolean configured,
        String model,
        String baseUrl,
        int ragDocuments,
        List<String> tools,
        List<String> pipeline,
        List<String> crew,
        boolean localFallback
) {
}
