package com.weatherhub.ai.agent;

import com.weatherhub.ai.dto.TraceStep;
import com.weatherhub.ai.llm.LlmMessage;
import com.weatherhub.ai.rag.ProjectKnowledge;
import com.weatherhub.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class PythonAgentClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiProperties properties;

    public PythonAgentClient(AiProperties properties) {
        this.properties = properties;
    }

    public boolean configured() {
        return properties.hasPythonAgentUrl();
    }

    public ReviewResult review(
            Long userId,
            Long sessionId,
            String message,
            List<LlmMessage> history,
            List<String> evidences,
            List<String> usedTools,
            List<String> ragSources,
            List<TraceStep> trace
    ) {
        if (!configured()) {
            return null;
        }
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    // Uvicorn does not support cleartext HTTP/2 upgrades; the body can be lost.
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(Math.max(10, properties.getTimeoutSeconds())));
            RestClient client = RestClient.builder()
                    .baseUrl(properties.normalizedPythonAgentUrl())
                    .requestFactory(factory)
                    .build();
            PythonReviewResponse response = client.post()
                    .uri("/agent/review")
                    .body(reviewBody(userId, sessionId, message, history, evidences, usedTools, ragSources, trace))
                    .retrieve()
                    .body(PythonReviewResponse.class);
            if (response == null || !StringUtils.hasText(response.reply())) {
                return null;
            }
            return new ReviewResult(response.mode(), response.reply(), response.trace() == null ? List.of() : response.trace());
        } catch (Exception ex) {
            log.warn("Python Agent unavailable: {}", ex.getMessage());
            return null;
        }
    }

    public ReviewResult reviewStream(
            Long userId,
            Long sessionId,
            String message,
            List<LlmMessage> history,
            List<String> evidences,
            List<String> usedTools,
            List<String> ragSources,
            List<TraceStep> trace,
            Consumer<String> onDelta
    ) {
        if (!configured()) {
            return null;
        }
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String payload = JSON.writeValueAsString(
                    reviewBody(userId, sessionId, message, history, evidences, usedTools, ragSources, trace)
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.normalizedPythonAgentUrl() + "/agent/review/stream"))
                    .timeout(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                log.warn("Python Agent stream returned {}", response.statusCode());
                return null;
            }
            StringBuilder full = new StringBuilder();
            String mode = "python-llm";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    JsonNode node = JSON.readTree(data);
                    if (node == null || node.isNull()) {
                        continue;
                    }
                    if (flag(node.get("done"))) {
                        if (node.get("mode") != null && !node.get("mode").isNull()) {
                            mode = node.get("mode").asString();
                        }
                        if (node.get("reply") != null && StringUtils.hasText(node.get("reply").asString())) {
                            if (full.isEmpty()) {
                                full.append(node.get("reply").asString());
                            }
                        }
                        break;
                    }
                    if (node.get("error") != null && StringUtils.hasText(node.get("error").asString())) {
                        log.warn("Python Agent stream error: {}", node.get("error").asString());
                        continue;
                    }
                    String text = node.get("text") == null || node.get("text").isNull() ? "" : node.get("text").asString();
                    if (StringUtils.hasText(text)) {
                        full.append(text);
                        if (onDelta != null) {
                            onDelta.accept(text);
                        }
                    }
                }
            }
            if (!StringUtils.hasText(full)) {
                return null;
            }
            return new ReviewResult(mode, full.toString().trim(), trace);
        } catch (Exception ex) {
            log.warn("Python Agent stream unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private static Map<String, Object> reviewBody(
            Long userId,
            Long sessionId,
            String message,
            List<LlmMessage> history,
            List<String> evidences,
            List<String> usedTools,
            List<String> ragSources,
            List<TraceStep> trace
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", userId);
        body.put("session_id", sessionId);
        body.put("message", message);
        body.put("project_context", ProjectKnowledge.context());
        body.put("history", history.stream().map(item -> Map.of("role", item.role(), "content", item.content())).toList());
        body.put("evidences", evidences);
        body.put("used_tools", usedTools);
        body.put("rag_sources", ragSources);
        body.put("trace", trace);
        return body;
    }

    private static boolean flag(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        String value = node.asString();
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public record ReviewResult(String mode, String reply, List<TraceStep> trace) {
    }

    public record PythonReviewResponse(String reply, String mode, List<TraceStep> trace) {
    }
}
