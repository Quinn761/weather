package com.weatherhub.ai.llm;

import com.weatherhub.common.BusinessException;
import com.weatherhub.config.AiProperties;
import org.springframework.http.HttpHeaders;
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class LlmClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiProperties properties;
    private final RestClient restClient;
    private final HttpClient httpClient;

    public LlmClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, properties.getTimeoutSeconds())));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.normalizedBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (properties.hasApiKey()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim());
        }
        this.restClient = builder.build();
    }

    public boolean configured() {
        return properties.isEnabled() && properties.hasApiKey();
    }

    public LlmMessage chat(List<LlmMessage> messages, List<Map<String, Object>> tools) {
        if (!configured()) {
            throw new BusinessException(503, "尚未配置大模型 API Key。请设置 AI_API_KEY。默认对接 DeepSeek（deepseek-chat）。");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages.stream().map(this::toPayload).toList());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        String raw = post("/chat/completions", body);
        return parseChat(raw);
    }

    public String chatStream(List<LlmMessage> messages, Consumer<String> onDelta) {
        if (!configured()) {
            throw new BusinessException(503, "尚未配置大模型 API Key。请设置 AI_API_KEY。默认对接 DeepSeek（deepseek-chat）。");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages.stream().map(this::toPayload).toList());
        body.put("stream", true);
        String payload = JSON.writeValueAsString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(properties.normalizedBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (properties.hasApiKey()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim());
        }
        try {
            HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException(502, describeLlmError(response.statusCode(), errorBody));
            }
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        continue;
                    }
                    String delta = extractStreamDelta(data);
                    if (StringUtils.hasText(delta)) {
                        full.append(delta);
                        if (onDelta != null) {
                            onDelta.accept(delta);
                        }
                    }
                }
            }
            return full.toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(502, "调用大模型失败：" + ex.getMessage());
        }
    }

    static String extractStreamDelta(String json) {
        JsonNode root = JSON.readTree(json);
        JsonNode choices = root == null ? null : root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode delta = choices.get(0).get("delta");
        if (delta == null || delta.isNull()) {
            return text(choices.get(0).get("text"));
        }
        return text(delta.get("content"));
    }

    public List<Double> embed(String text) {
        if (!configured() || !properties.hasEmbeddingModel()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbeddingModel());
        body.put("input", text);
        String raw = post("/embeddings", body);
        JsonNode root = JSON.readTree(raw);
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            return List.of();
        }
        JsonNode embedding = data.get(0).get("embedding");
        if (embedding == null || !embedding.isArray()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>();
        embedding.forEach(item -> values.add(item.doubleValue()));
        return values;
    }

    private String post(String path, Map<String, Object> body) {
        String payload = JSON.writeValueAsString(body);
        try {
            return restClient.post()
                    .uri(path)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes());
                        throw new BusinessException(502, describeLlmError(response.getStatusCode().value(), errorBody));
                    })
                    .body(String.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(502, "调用大模型失败：" + ex.getMessage());
        }
    }

    private LlmMessage parseChat(String raw) {
        JsonNode root = JSON.readTree(raw);
        JsonNode choices = root == null ? null : root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new BusinessException(502, "大模型返回空结果：" + shorten(raw));
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || message.isNull()) {
            throw new BusinessException(502, "大模型没有返回 message");
        }
        String content = text(message.get("content"));
        JsonNode toolCallsNode = message.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<LlmToolCall> calls = new ArrayList<>();
            toolCallsNode.forEach(item -> {
                JsonNode fn = item.get("function");
                String id = text(item.get("id"));
                String name = fn == null ? "" : text(fn.get("name"));
                String arguments = fn == null ? "{}" : text(fn.get("arguments"));
                if (arguments.isBlank()) {
                    arguments = "{}";
                }
                calls.add(new LlmToolCall(id, name, arguments));
            });
            return LlmMessage.assistantTools(calls);
        }
        return LlmMessage.assistant(content);
    }

    private Map<String, Object> toPayload(LlmMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role());
        if (message.content() != null) {
            payload.put("content", message.content());
        }
        if (message.name() != null) {
            payload.put("name", message.name());
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream().map(call -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", call.id());
                item.put("type", "function");
                item.put("function", Map.of(
                        "name", call.name(),
                        "arguments", call.arguments() == null ? "{}" : call.arguments()
                ));
                return item;
            }).toList());
        }
        return payload;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asString();
    }

    private static String describeLlmError(int status, String errorBody) {
        String body = errorBody == null ? "" : errorBody;
        String lower = body.toLowerCase();
        if (status == 402 || lower.contains("insufficient balance") || lower.contains("insufficient_quota")) {
            return "DeepSeek 账号余额不足。请到 https://platform.deepseek.com/top_up 充值后再试。密钥是对的，只是账户没额度。";
        }
        if (status == 401 || lower.contains("authentication") || lower.contains("invalid api key")) {
            return "大模型 API Key 无效或已失效，请检查 AI_API_KEY。";
        }
        if (status == 429) {
            return "大模型请求过于频繁，请稍后再试。";
        }
        return "大模型接口返回 " + status + "：" + shorten(body);
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 400 ? trimmed.substring(0, 400) + "…" : trimmed;
    }
}
