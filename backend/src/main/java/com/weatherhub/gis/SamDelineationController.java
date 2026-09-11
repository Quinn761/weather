package com.weatherhub.gis;

import com.weatherhub.common.ApiResponse;
import com.weatherhub.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@RestController
@ConditionalOnProperty(name = "weatherhub.gis.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/gis/delineate")
public class SamDelineationController {
    private final RestClient client;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public SamDelineationController(
            @Value("${SAM2_SERVICE_URL:http://127.0.0.1:8000}") String serviceUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                // Uvicorn does not support the JDK client's cleartext HTTP/2 upgrade.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(150));
        client = RestClient.builder().baseUrl(serviceUrl).requestFactory(factory).build();
    }

    public record Bounds(@NotNull Double west, @NotNull Double south,
                         @NotNull Double east, @NotNull Double north) {}

    public record Request(@NotBlank @Size(max = 8_000_000) String imageBase64,
                          @NotNull @Valid Bounds bounds,
                          @NotNull Double longitude, @NotNull Double latitude) {}

    @PostMapping("/sam2")
    public ApiResponse<JsonNode> delineate(@Valid @RequestBody Request request) {
        try {
            String body = client.post().uri("/gis/delineate/sam2").body(request)
                    .retrieve().body(String.class);
            if (body == null) throw new BusinessException("SAM 2.1 返回了空结果");
            return ApiResponse.ok(JSON.readTree(body));
        } catch (RestClientResponseException ex) {
            String message = "SAM 2.1 推理失败，请检查 Python 服务日志";
            try {
                JsonNode detail = JSON.readTree(ex.getResponseBodyAsString()).get("detail");
                if (detail != null && detail.isString()) message = detail.asString();
                else if (detail != null && detail.isArray() && !detail.isEmpty()) {
                    JsonNode reason = detail.get(0).get("msg");
                    if (reason != null && reason.isString()) {
                        message = "SAM 2.1 请求参数无效：" + reason.asString();
                    }
                }
            } catch (Exception ignored) {
                // Keep the useful service-level message for non-JSON gateway responses.
            }
            throw new BusinessException(message);
        } catch (RestClientException ex) {
            throw new BusinessException("SAM 2.1 服务连接失败或超时，请检查 SAM2_SERVICE_URL 和 Python 服务");
        }
    }
}
