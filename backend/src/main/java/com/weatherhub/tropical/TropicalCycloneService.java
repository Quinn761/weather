package com.weatherhub.tropical;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
public class TropicalCycloneService {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String apiKey;
    private final RestClient client;
    private volatile CacheEntry cache;

    public TropicalCycloneService(
            @Value("${weatherhub.qweather.api-key:}") String apiKey,
            @Value("${weatherhub.qweather.api-host:https://devapi.qweather.com}") String apiHost) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(apiHost).requestFactory(factory).build();
    }

    public TropicalOverview overview() {
        if (apiKey.isBlank()) return new TropicalOverview(false, null, List.of());
        CacheEntry current = cache;
        if (current != null && current.expiresAt().isAfter(Instant.now())) return current.overview();
        synchronized (this) {
            current = cache;
            if (current != null && current.expiresAt().isAfter(Instant.now())) return current.overview();
            TropicalOverview overview = new TropicalOverview(true, Instant.now().toString(), fetchActiveStorms());
            cache = new CacheEntry(overview, Instant.now().plus(Duration.ofMinutes(10)));
            return overview;
        }
    }

    private List<Storm> fetchActiveStorms() {
        try {
            String body = client.get().uri(builder -> builder.path("/v7/tropical/storm-list")
                    .queryParam("basin", "NP").queryParam("year", Year.now().getValue())
                    .queryParam("key", apiKey).build()).retrieve().body(String.class);
            JsonNode root = body == null ? null : JSON.readTree(body);
            if (root == null || !"200".equals(text(root, "code"))) return List.of();
            List<Storm> storms = new ArrayList<>();
            JsonNode items = root.get("storm");
            if (items == null || !items.isArray()) return storms;
            for (JsonNode item : items) {
                if (!"1".equals(text(item, "isActive"))) continue;
                Storm storm = fetchTrack(text(item, "id"), text(item, "name"));
                if (storm != null) storms.add(storm);
            }
            return storms;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Storm fetchTrack(String id, String name) {
        if (id.isBlank()) return null;
        try {
            String body = client.get().uri(builder -> builder.path("/v7/tropical/storm-track")
                    .queryParam("stormid", id).queryParam("key", apiKey).build()).retrieve().body(String.class);
            JsonNode root = body == null ? null : JSON.readTree(body);
            if (root == null || !"200".equals(text(root, "code"))) return null;
            JsonNode now = root.get("now");
            if (now == null || now.isNull()) return null;
            List<TrackPoint> track = new ArrayList<>();
            JsonNode items = root.get("track");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) track.add(point(item, "time"));
            }
            track.add(point(now, "time"));
            List<TrackPoint> forecast = fetchForecast(id);
            return new Storm(
                    id,
                    name,
                    text(now, "lat"),
                    text(now, "lon"),
                    text(now, "type"),
                    text(now, "pressure"),
                    text(now, "windSpeed"),
                    text(now, "moveSpeed"),
                    text(now, "moveDir"),
                    track,
                    forecast,
                    windRadius(now, "windRadius30"),
                    windRadius(now, "windRadius50"),
                    windRadius(now, "windRadius64")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<TrackPoint> fetchForecast(String id) {
        try {
            String body = client.get().uri(builder -> builder.path("/v7/tropical/storm-forecast")
                    .queryParam("stormid", id).queryParam("key", apiKey).build()).retrieve().body(String.class);
            JsonNode root = body == null ? null : JSON.readTree(body);
            if (root == null || !"200".equals(text(root, "code"))) return List.of();
            JsonNode items = root.get("forecast");
            if (items == null || !items.isArray()) return List.of();
            List<TrackPoint> forecast = new ArrayList<>();
            for (JsonNode item : items) forecast.add(point(item, "fxTime"));
            return forecast;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private TrackPoint point(JsonNode node, String timeField) {
        return new TrackPoint(
                text(node, timeField),
                text(node, "lat"),
                text(node, "lon"),
                text(node, "type"),
                text(node, "pressure"),
                text(node, "windSpeed")
        );
    }

    private WindRadius windRadius(JsonNode node, String field) {
        JsonNode radius = node.get(field);
        if (radius == null || radius.isNull()) return null;
        String ne = text(radius, "neRadius");
        String se = text(radius, "seRadius");
        String sw = text(radius, "swRadius");
        String nw = text(radius, "nwRadius");
        if (ne.isBlank() && se.isBlank() && sw.isBlank() && nw.isBlank()) return null;
        return new WindRadius(ne, se, sw, nw);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private record CacheEntry(TropicalOverview overview, Instant expiresAt) { }

    public record TropicalOverview(boolean configured, String updatedAt, List<Storm> storms) { }

    public record Storm(
            String id,
            String name,
            String lat,
            String lon,
            String type,
            String pressure,
            String windSpeed,
            String moveSpeed,
            String moveDir,
            List<TrackPoint> track,
            List<TrackPoint> forecast,
            WindRadius windRadius30,
            WindRadius windRadius50,
            WindRadius windRadius64
    ) { }

    public record TrackPoint(String time, String lat, String lon, String type, String pressure, String windSpeed) { }

    public record WindRadius(String neRadius, String seRadius, String swRadius, String nwRadius) { }
}
