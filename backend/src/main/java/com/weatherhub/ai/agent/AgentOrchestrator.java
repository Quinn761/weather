package com.weatherhub.ai.agent;

import com.weatherhub.ai.dto.ChatRequest;
import com.weatherhub.ai.dto.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final AgentRuntime runtime;

    public AgentOrchestrator(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    public ChatResponse chat(ChatRequest request) {
        return runtime.chat(request);
    }
}
