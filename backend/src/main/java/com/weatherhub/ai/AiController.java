package com.weatherhub.ai;

import com.weatherhub.ai.agent.AgentOrchestrator;
import com.weatherhub.ai.agent.AgentRuntime;
import com.weatherhub.ai.dto.AgentRunRequest;
import com.weatherhub.ai.dto.AgentRunVO;
import com.weatherhub.ai.dto.AgentStreamEvent;
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
import com.weatherhub.config.JevProperties;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
    private final JevProperties jevProperties;

    public AiController(
            AgentOrchestrator orchestrator,
            AgentRuntime runtime,
            AgentWorkspace workspace,
            LlmClient llmClient,
            AiProperties properties,
            RagService ragService,
            McpToolCatalog catalog,
            JevProperties jevProperties
    ) {
        this.orchestrator = orchestrator;
        this.runtime = runtime;
        this.workspace = workspace;
        this.llmClient = llmClient;
        this.properties = properties;
        this.ragService = ragService;
        this.catalog = catalog;
        this.jevProperties = jevProperties;
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
                true,
                jevProperties.configured()
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

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(@Valid @RequestBody AgentRunRequest request, Authentication authentication) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> emitter.complete());
        Long userId = Long.valueOf(authentication.getName());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        Thread.ofVirtual().name("ai-stream").start(() -> {
            SecurityContextHolder.setContext(context);
            try {
                runtime.runStream(userId, request, event -> {
                    try {
                        emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
                try {
                    String message = ex.getMessage() == null ? "Agent 执行失败" : ex.getMessage();
                    emitter.send(SseEmitter.event().data(AgentStreamEvent.error(message), MediaType.APPLICATION_JSON));
                } catch (Exception ignored) {
                    // client already gone
                }
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
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
