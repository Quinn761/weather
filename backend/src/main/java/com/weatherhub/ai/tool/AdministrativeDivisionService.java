package com.weatherhub.ai.tool;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached DataV administrative divisions used to expand a province into weather locations. */
@Service
public class AdministrativeDivisionService {
    private static final String COUNTRY = "100000";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AdministrativeDivisionService() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Returns direct city-level children for the supplied province name, using cached GeoJSON when available. */
    public List<Area> citiesForProvince(String provinceName) {
        Area province = areas(COUNTRY).stream()
                .filter(area -> sameArea(provinceName, area.name()))
                .findFirst()
                .orElse(null);
        if (province == null || !"province".equals(province.level())) return List.of();
        return areas(province.adcode()).stream().filter(area -> area.latitude() != null && area.longitude() != null).toList();
    }

    /**
     * Resolves a mainland city/county from the same DataV administrative data.
     * This avoids selecting a same-named overseas or domestic locality returned
     * first by a generic geocoder. Results are retained by the 24-hour cache.
     */
    public Area findArea(String placeName) {
        if (!StringUtils.hasText(placeName)) return null;
        List<Area> provinces = areas(COUNTRY);
        Area province = provinces.stream().filter(area -> sameArea(placeName, area.name())).findFirst().orElse(null);
        if (hasCoordinates(province)) return province;
        for (Area candidate : provinces) {
            Area match = areas(candidate.adcode()).stream()
                    .filter(area -> sameArea(placeName, area.name()))
                    .filter(AdministrativeDivisionService::hasCoordinates)
                    .findFirst()
                    .orElse(null);
            if (match != null) return match;
        }
        return null;
    }

    private List<Area> areas(String adcode) {
        CacheEntry current = cache.get(adcode);
        if (current != null && current.expiresAt().isAfter(Instant.now())) return current.areas();
        synchronized (cache) {
            current = cache.get(adcode);
            if (current != null && current.expiresAt().isAfter(Instant.now())) return current.areas();
            List<Area> loaded = load(adcode);
            cache.put(adcode, new CacheEntry(loaded, Instant.now().plus(CACHE_TTL)));
            return loaded;
        }
    }

    private List<Area> load(String adcode) {
        try {
            String raw = http.get().uri("https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json", adcode)
                    .retrieve().body(String.class);
            JsonNode features = JSON.readTree(raw == null ? "{}" : raw).path("features");
            if (!features.isArray()) return List.of();
            List<Area> result = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties");
                JsonNode center = properties.path("center");
                Double longitude = center.isArray() && center.size() >= 2 ? number(center.get(0)) : null;
                Double latitude = center.isArray() && center.size() >= 2 ? number(center.get(1)) : null;
                String name = text(properties.get("name"));
                String code = text(properties.get("adcode"));
                if (StringUtils.hasText(name) && StringUtils.hasText(code)) {
                    result.add(new Area(name, code, text(properties.get("level")), latitude, longitude));
                }
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static boolean sameArea(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static boolean hasCoordinates(Area area) {
        return area != null && area.latitude() != null && area.longitude() != null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("特别行政区", "").replace("自治区", "").replace("省", "").replace("市", "")
                .replace("维吾尔", "").replace("壮族", "").replace("回族", "").replace("藏族", "")
                .trim();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asString();
    }

    private static Double number(JsonNode node) {
        try { return node == null || node.isNull() ? null : Double.parseDouble(node.asString()); }
        catch (NumberFormatException ex) { return null; }
    }

    private record CacheEntry(List<Area> areas, Instant expiresAt) { }
    public record Area(String name, String adcode, String level, Double latitude, Double longitude) { }
}
