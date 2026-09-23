package com.weatherhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "weatherhub.jev")
public class JevProperties {

    /** Enables Jev only when a server-side API key is also present. */
    private boolean enabled = false;

    private String apiKey = "";

    private String baseUrl = "https://api.typesafe.ai";

    /** Model alias advertised by the Jev API. */
    private String model = "jev-latest";

    private int timeoutSeconds = 8;

    /** Do not trust a route below this confidence; use the existing planner instead. */
    private double minimumConfidence = 0.75;

    public boolean configured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    public String endpoint() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/v1/systemone") ? base : base + "/v1/systemone";
    }
}
