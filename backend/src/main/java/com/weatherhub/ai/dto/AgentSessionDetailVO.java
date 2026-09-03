package com.weatherhub.ai.dto;

import java.util.List;

public record AgentSessionDetailVO(
        AgentSessionVO session,
        List<AgentMessageVO> messages,
        List<String> memories
) {
}
