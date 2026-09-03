package com.weatherhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "weatherhub.ai")
public class AiProperties {

    private boolean enabled = true;

    /**
     * OpenAI 兼容地址。DeepSeek 用 https://api.deepseek.com ；
     * 通义用 https://dashscope.aliyuncs.com/compatible-mode/v1 。
     */
    private String baseUrl = "https://api.deepseek.com";

    private String apiKey = "";

    private String model = "deepseek-chat";

    /** 为空则 RAG 走关键词，不调 embedding 接口。 */
    private String embeddingModel = "";

    private int timeoutSeconds = 90;

    private int maxToolRounds = 6;

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    public boolean hasEmbeddingModel() {
        return StringUtils.hasText(embeddingModel);
    }

    public String normalizedBaseUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.contains("/v1")) {
            base = base + "/v1";
        }
        return base;
    }
}
