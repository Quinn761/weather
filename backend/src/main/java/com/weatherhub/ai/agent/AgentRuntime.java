package com.weatherhub.ai.agent;

import com.weatherhub.ai.dto.AgentRunRequest;
import com.weatherhub.ai.dto.AgentRunVO;
import com.weatherhub.ai.dto.AgentTaskVO;
import com.weatherhub.ai.dto.ChatRequest;
import com.weatherhub.ai.dto.ChatResponse;
import com.weatherhub.ai.dto.TraceStep;
import com.weatherhub.ai.llm.LlmClient;
import com.weatherhub.ai.llm.LlmMessage;
import com.weatherhub.ai.rag.RagHit;
import com.weatherhub.ai.rag.RagService;
import com.weatherhub.ai.store.AgentWorkspace;
import com.weatherhub.ai.store.AiSession;
import com.weatherhub.ai.tool.McpToolCatalog;
import com.weatherhub.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentRuntime {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> CREW = List.of("planner", "knowledge", "operator", "reviewer");

    private final AgentWorkspace workspace;
    private final RagService ragService;
    private final McpToolCatalog catalog;
    private final LlmClient llmClient;

    public AgentRuntime(AgentWorkspace workspace, RagService ragService, McpToolCatalog catalog, LlmClient llmClient) {
        this.workspace = workspace;
        this.ragService = ragService;
        this.catalog = catalog;
        this.llmClient = llmClient;
    }

    public ChatResponse chat(ChatRequest request) {
        AgentRunVO run = run(currentUserId(), new AgentRunRequest(null, request.message()));
        return new ChatResponse(run.reply(), run.mode(), llmClient.configured(), run.trace(), run.usedTools(), run.ragSources());
    }

    public AgentRunVO run(Long userId, AgentRunRequest request) {
        String question = request.message().trim();
        AiSession session = request.sessionId() == null
                ? workspace.createSession(userId, question)
                : workspace.requireSession(userId, request.sessionId());
        workspace.retitleIfPlaceholder(session, question);
        workspace.saveMessage(session.getId(), "user", question, "user", null);

        List<TraceStep> trace = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();
        List<String> ragSources = new ArrayList<>();
        List<String> evidences = new ArrayList<>();
        List<PlanStep> steps = HeuristicPlanner.plan(question);
        List<AgentTaskVO> plan = new ArrayList<>();
        trace.add(new TraceStep("agent", "规划官拆解为 " + steps.size() + " 步"));

        for (PlanStep step : steps) {
            if ("planner".equals(step.agent())) {
                plan.add(new AgentTaskVO(step.agent(), step.title(), "done", "已分配 " + String.join("、", CREW)));
                continue;
            }
            if ("knowledge".equals(step.agent())) {
                List<RagHit> hits = ragService.retrieve(question, 3);
                ragSources.addAll(hits.stream().map(RagHit::title).toList());
                String knowledge;
                if (hits.isEmpty()) {
                    knowledge = catalog.call("search_knowledge", "{\"query\":" + JSON.writeValueAsString(question) + "}");
                    usedTools.add("search_knowledge");
                    trace.add(new TraceStep("mcp", "知识官通过 MCP 目录检索"));
                } else {
                    knowledge = hits.stream().map(hit -> "《" + hit.title() + "》" + hit.content()).reduce((a, b) -> a + "\n" + b).orElse("");
                    trace.add(new TraceStep("rag", "命中 " + ragSources));
                }
                evidences.add("知识官：" + knowledge);
                plan.add(new AgentTaskVO(step.agent(), step.title(), "done", clip(knowledge, 180)));
                continue;
            }
            if ("operator".equals(step.agent()) && StringUtils.hasText(step.tool())) {
                String args = JSON.writeValueAsString(Map.of("query", question));
                String result = catalog.call(step.tool(), args);
                usedTools.add(step.tool());
                evidences.add("执行官/" + step.tool() + "：" + result);
                trace.add(new TraceStep("tool", "执行官调用 " + step.tool()));
                trace.add(new TraceStep("mcp", "MCP 目录执行 " + step.tool()));
                plan.add(new AgentTaskVO(step.agent(), step.title(), "done", clip(result, 180)));
                continue;
            }
            if ("reviewer".equals(step.agent())) {
                Review result = review(question, evidences);
                plan.add(new AgentTaskVO(step.agent(), step.title(), "done", result.mode()));
                trace.add(new TraceStep("prompt", "质检官汇总证据"));
                trace.add(new TraceStep("llm", result.mode().equals("llm") ? "大模型生成最终回答" : "大模型不可用，本地质检官交卷"));
                workspace.remember(userId, "last_goal", question);
                workspace.remember(userId, "last_mode", result.mode());
                String payload = JSON.writeValueAsString(Map.of(
                        "plan", plan,
                        "mode", result.mode(),
                        "usedTools", usedTools,
                        "ragSources", ragSources,
                        "trace", trace
                ));
                workspace.saveMessage(session.getId(), "assistant", result.reply(), "reviewer", payload);
                List<String> memories = workspace.memories(userId);
                return new AgentRunVO(
                        session.getId(),
                        session.getTitle(),
                        result.reply(),
                        result.mode(),
                        CREW,
                        plan,
                        usedTools,
                        ragSources,
                        trace,
                        memories
                );
            }
        }
        throw new BusinessException("Agent 未产出回答");
    }

    private Review review(String question, List<String> evidences) {
        String evidenceText = evidences.isEmpty() ? "没有额外证据。" : String.join("\n\n", evidences);
        if (llmClient.configured()) {
            try {
                LlmMessage answer = llmClient.chat(List.of(
                        LlmMessage.system("你是 Weather Data Hub 的质检官。只用下面证据回答，不要编造数字。"
                                + "如果证据里有 Open-Meteo：先给结论（会不会下雨、空气好不好、冷不冷），再引用降水概率、AQI、PM2.5、气温。"
                                + "数字必须来自证据。用简体中文。"),
                        LlmMessage.user("用户目标：" + question + "\n\n证据：\n" + evidenceText)
                ), List.of());
                if (StringUtils.hasText(answer.content())) {
                    return new Review("llm", answer.content().trim());
                }
            } catch (BusinessException ex) {
                return new Review("local", localReply(question, evidenceText) + "\n\n（大模型未生成：" + ex.getMessage() + "）");
            }
        }
        return new Review("local", localReply(question, evidenceText));
    }

    private static String localReply(String question, String evidenceText) {
        return "【规划官】已把「" + question + "」拆给知识官和执行官。\n\n"
                + evidenceText + "\n\n"
                + "【质检官】以上为系统内真实检索/工具结果。若要更口语化的总结，请给 DeepSeek 充值后重试。";
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return Long.valueOf(authentication.getName());
    }

    private static String clip(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > limit ? trimmed.substring(0, limit) + "…" : trimmed;
    }

    private record Review(String mode, String reply) {
    }
}
