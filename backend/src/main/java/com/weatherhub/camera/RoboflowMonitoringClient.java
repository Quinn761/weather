package com.weatherhub.camera;

import com.weatherhub.common.BusinessException;
import com.weatherhub.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sends a private camera snapshot to the Python service; the Roboflow key never reaches the browser. */
@Component
@RequiredArgsConstructor
public class RoboflowMonitoringClient {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AiProperties properties;

    public Map<String, Object> analyze(Path image) {
        if (!properties.hasPythonAgentUrl()) {
            throw new BusinessException(503, "AI monitoring service is not configured");
        }
        try {
            String boundary = "WeatherHub-" + UUID.randomUUID();
            String filename = image.getFileName().toString().replace("\"", "_");
            byte[] opening = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] closing = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.normalizedPythonAgentUrl() + "/camera-monitoring/analyze"))
                    .timeout(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(
                            List.of(opening, Files.readAllBytes(image), closing)
                    ))
                    .build();
            HttpResponse<String> httpResponse = HttpClient.newBuilder()
                    // Uvicorn does not support cleartext HTTP/2 upgrades; such an upgrade loses the request body.
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() >= 400) {
                throw new BusinessException(502, "AI monitoring service returned "
                        + httpResponse.statusCode() + ": " + httpResponse.body());
            }
            Map response = JSON.readValue(httpResponse.body(), Map.class);
            if (response == null) {
                throw new BusinessException(502, "AI monitoring service returned no result");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
            throw new BusinessException(502, "AI monitoring failed: " + message);
        }
    }
}
