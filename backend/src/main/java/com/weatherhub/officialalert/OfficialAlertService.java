package com.weatherhub.officialalert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OfficialAlertService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Region> REGIONS = List.of(
            new Region("北京市", "101010100"), new Region("天津市", "101030100"),
            new Region("上海市", "101020100"), new Region("重庆市", "101040100"),
            new Region("河北省", "101090101"), new Region("山西省", "101100101"),
            new Region("辽宁省", "101070101"), new Region("吉林省", "101060101"),
            new Region("黑龙江省", "101050101"), new Region("江苏省", "101190101"),
            new Region("浙江省", "101210101"), new Region("安徽省", "101220101"),
            new Region("福建省", "101230101"), new Region("江西省", "101240101"),
            new Region("山东省", "101120101"), new Region("河南省", "101180101"),
            new Region("湖北省", "101200101"), new Region("湖南省", "101250101"),
            new Region("广东省", "101280101"), new Region("海南省", "101310101"),
            new Region("四川省", "101270101"), new Region("贵州省", "101260101"),
            new Region("云南省", "101290101"), new Region("陕西省", "101110101"),
            new Region("甘肃省", "101160101"), new Region("青海省", "101150101"),
            new Region("台湾省", "101340101"), new Region("内蒙古自治区", "101080101"),
            new Region("广西壮族自治区", "101300101"), new Region("西藏自治区", "101140101"),
            new Region("宁夏回族自治区", "101170101"), new Region("新疆维吾尔自治区", "101130101"),
            new Region("香港特别行政区", "101320101"), new Region("澳门特别行政区", "101330101")
    );

    private final String apiKey;
    private final RestClient client;
    private final Duration cacheDuration;
    private volatile CacheEntry cache;

    public OfficialAlertService(
            @Value("${weatherhub.qweather.api-key:}") String apiKey,
            @Value("${weatherhub.qweather.api-host:https://devapi.qweather.com}") String apiHost,
            @Value("${weatherhub.qweather.cache-minutes:60}") long cacheMinutes) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.cacheDuration = Duration.ofMinutes(Math.max(5, cacheMinutes));
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        factory.setReadTimeout(Duration.ofSeconds(12));
        this.client = RestClient.builder().baseUrl(apiHost).requestFactory(factory).build();
    }

    public OfficialAlertOverview overview() {
        if (apiKey.isBlank()) return new OfficialAlertOverview(false, false, null, List.of());
        CacheEntry current = cache;
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return new OfficialAlertOverview(true, true, current.updatedAt().toString(), current.regions());
        }
        synchronized (this) {
            current = cache;
            if (current != null && current.expiresAt().isAfter(Instant.now())) {
                return new OfficialAlertOverview(true, true, current.updatedAt().toString(), current.regions());
            }
            List<RegionAlert> regions = REGIONS.parallelStream().map(this::fetchRegion).toList();
            Instant updatedAt = Instant.now();
            cache = new CacheEntry(updatedAt, updatedAt.plus(cacheDuration), regions);
            return new OfficialAlertOverview(true, false, updatedAt.toString(), regions);
        }
    }

    private RegionAlert fetchRegion(Region region) {
        try {
            String body = client.get().uri(builder -> builder.path("/v7/warning/now")
                    .queryParam("location", region.locationId())
                    .queryParam("key", apiKey).build()).retrieve().body(String.class);
            if (body == null) return new RegionAlert(region.name(), region.locationId(), List.of());
            JsonNode root = JSON.readTree(body);
            JsonNode code = root.get("code");
            if (code == null || !"200".equals(code.asString())) return new RegionAlert(region.name(), region.locationId(), List.of());
            JsonNode warnings = root.get("warning");
            List<Warning> results = new ArrayList<>();
            if (warnings != null && warnings.isArray()) {
                for (JsonNode warning : warnings) {
                    results.add(new Warning(
                            text(warning, "id"), text(warning, "title"), text(warning, "typeName"),
                            text(warning, "level"), text(warning, "sender"), text(warning, "pubTime"), text(warning, "text")
                    ));
                }
            }
            return new RegionAlert(region.name(), region.locationId(), results);
        } catch (Exception ignored) {
            return new RegionAlert(region.name(), region.locationId(), List.of());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private record Region(String name, String locationId) { }
    private record CacheEntry(Instant updatedAt, Instant expiresAt, List<RegionAlert> regions) { }
    public record OfficialAlertOverview(boolean configured, boolean cached, String updatedAt, List<RegionAlert> regions) { }
    public record RegionAlert(String regionName, String locationId, List<Warning> warnings) { }
    public record Warning(String id, String title, String typeName, String level, String sender, String pubTime, String text) { }
}
