package com.weatherhub.ai;

import com.weatherhub.ai.agent.AgentOrchestrator;
import com.weatherhub.ai.agent.AgentRuntime;
import com.weatherhub.ai.dto.AgentRunRequest;
import com.weatherhub.ai.dto.AgentRunVO;
import com.weatherhub.ai.dto.AgentSessionDetailVO;
import com.weatherhub.ai.dto.AgentSessionVO;
import com.weatherhub.ai.dto.AiStatusVO;
import com.weatherhub.ai.dto.ChatRequest;
import com.weatherhub.ai.dto.ChatResponse;
import com.weatherhub.ai.llm.LlmClient;
import com.weatherhub.ai.rag.RagService;
import com.weatherhub.ai.store.AgentWorkspace;
import com.weatherhub.ai.store.AiSession;
import com.weatherhub.ai.tool.McpToolCatalog;
import com.weatherhub.common.ApiResponse;
import com.weatherhub.config.AiProperties;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final List<String> PIPELINE = List.of("llm", "prompt", "tool", "rag", "mcp", "agent");
    private static final List<String> CREW = List.of("planner", "knowledge", "operator", "reviewer");

    private final AgentOrchestrator orchestrator;
    private final AgentRuntime runtime;
    private final AgentWorkspace workspace;
    private final LlmClient llmClient;
    private final AiProperties properties;
    private final RagService ragService;
    private final McpToolCatalog catalog;

    public AiController(
            AgentOrchestrator orchestrator,
            AgentRuntime runtime,
            AgentWorkspace workspace,
            LlmClient llmClient,
            AiProperties properties,
            RagService ragService,
            McpToolCatalog catalog
    ) {
        this.orchestrator = orchestrator;
        this.runtime = runtime;
        this.workspace = workspace;
        this.llmClient = llmClient;
        this.properties = properties;
        this.ragService = ragService;
        this.catalog = catalog;
    }

    @GetMapping("/status")
    public ApiResponse<AiStatusVO> status() {
        return ApiResponse.ok(new AiStatusVO(
                llmClient.configured(),
                properties.getModel(),
                properties.normalizedBaseUrl(),
                ragService.documentCount(),
                catalog.names(),
                PIPELINE,
                CREW,
                true
        ));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(orchestrator.chat(request));
    }

    @PostMapping("/run")
    public ApiResponse<AgentRunVO> run(@Valid @RequestBody AgentRunRequest request, Authentication authentication) {
        return ApiResponse.ok(runtime.run(Long.valueOf(authentication.getName()), request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AgentSessionVO>> sessions(Authentication authentication) {
        return ApiResponse.ok(workspace.listSessions(Long.valueOf(authentication.getName())));
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSessionVO> createSession(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        AiSession session = workspace.createSession(userId, "新任务");
        return ApiResponse.ok(new AgentSessionVO(session.getId(), session.getTitle(), session.getUpdatedAt()));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<AgentSessionDetailVO> session(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(workspace.detail(Long.valueOf(authentication.getName()), id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id, Authentication authentication) {
        workspace.deleteSession(Long.valueOf(authentication.getName()), id);
        return ApiResponse.ok();
    }

    @GetMapping("/memory")
    public ApiResponse<Map<String, List<String>>> memory(Authentication authentication) {
        return ApiResponse.ok(Map.of("items", workspace.memories(Long.valueOf(authentication.getName()))));
    }
}
