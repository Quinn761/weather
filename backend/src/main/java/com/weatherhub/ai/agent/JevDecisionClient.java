package com.weatherhub.ai.agent;

import com.weatherhub.config.JevProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Jev is deliberately limited to intent classification. */
@Component
public class JevDecisionClient {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> INTENTS = List.of("weather", "official_alert", "dashboard", "gis", "current_user", "system_knowledge", "chat");

    private final JevProperties properties;
    private final RestClient http;

    public JevDecisionClient(JevProperties properties) {
        this.properties = properties;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()))).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Returns an intent only: never a tool, permission, or workflow. */
    public Optional<IntentDecision> classify(String question) {
        if (!properties.configured() || !StringUtils.hasText(question)) return Optional.empty();
        try {
            Map<String, Object> payload = Map.of(
                    "model", properties.getModel(),
                    "state", "User request for Weather Data Hub: " + question.trim(),
                    "questions", Map.of("intent", Map.of(
                            "type", "choice",
                            "instructions", "Classify the user's request into exactly one supported Weather Data Hub intent. Do not recommend an action.",
                            "criteria", Map.of(
                                    "weather", "Current weather or forecast request",
                                    "official_alert", "Official disaster warning or alert request",
                                    "dashboard", "Dashboard overview or metrics request",
                                    "gis", "GIS layers, features, map, or annotation request",
                                    "current_user", "Current signed-in user or their permissions request",
                                    "system_knowledge", "Questions about product functionality, implementation, or knowledge base",
                                    "chat", "General conversation not requiring a system tool"
                            )
                    ))
            );
            String raw = http.post().uri(properties.endpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(String.class);
            return decision(raw);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<IntentDecision> decision(String raw) {
        JsonNode root = JSON.readTree(raw == null ? "{}" : raw);
        JsonNode answer = namedDecision(root, "intent");
        String intent = selected(answer);
        double confidence = confidence(answer);
        if (!INTENTS.contains(intent) || confidence < properties.getMinimumConfidence()) return Optional.empty();
        return Optional.of(new IntentDecision(intent, "jev", confidence));
    }

    private static JsonNode namedDecision(JsonNode root, String name) {
        for (String container : List.of("answers", "results", "data", "decisions", "questions")) {
            JsonNode candidate = root.path(container).get(name);
            if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) return candidate;
        }
        JsonNode direct = root.get(name);
        return direct == null ? JSON.readTree("{}") : direct;
    }

    private static String selected(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isString()) return node.asString().trim().toLowerCase(Locale.ROOT);
        for (String field : List.of("value", "answer", "choice", "result", "label", "output")) {
            JsonNode value = node.get(field);
            if (value != null && value.isString()) return value.asString().trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static double confidence(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        for (String field : List.of("confidence", "probability", "p")) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) return value.asDouble(0);
        }
        return 0;
    }
}
