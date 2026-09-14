package com.weatherhub.ai.tool;

import com.weatherhub.gis.GisFeatureService;
import com.weatherhub.gis.dto.GisFeatureVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Component
public class ListGisFeaturesTool implements AiTool {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final GisFeatureService gisFeatureService;

    public ListGisFeaturesTool(ObjectProvider<GisFeatureService> gis) {
        this.gisFeatureService = gis.getIfAvailable();
    }

    @Override
    public String name() {
        return "list_gis_features";
    }

    @Override
    public String description() {
        return "查询当前 PostGIS 地块标注的名称、ID、类型和经纬度范围，可按名称 keyword 或 id 过滤。问哪些地块、某地块在哪里时调用。不能根据坐标凭空断定行政区名称。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "按标注名称过滤，可空"),
                        "id", Map.of("type", "integer", "description", "按标注ID过滤，可空")
                )
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        if (gisFeatureService == null) {
            return "GIS 未启用。";
        }
        String keyword = textArg(arguments, "keyword");
        String id = textArg(arguments, "id");
        List<GisFeatureVO> features = gisFeatureService.list();
        List<GisFeatureVO> matched = features.stream()
                .filter(item -> !StringUtils.hasText(keyword) || (item.name() != null && item.name().contains(keyword)))
                .filter(item -> !StringUtils.hasText(id) || String.valueOf(item.id()).equals(id))
                .toList();
        if (matched.isEmpty()) {
            return features.isEmpty() ? "当前没有任何 GIS 标注。" : "没有符合名称/ID条件的标注。";
        }
        StringBuilder out = new StringBuilder("共 " + matched.size() + " 条标注：\n");
        out.append("最多返回前50条；坐标范围为WGS84经纬度，不包含行政区反向地理编码。\n");
        for (GisFeatureVO feature : matched.stream().limit(50).toList()) {
            out.append("- id=").append(feature.id())
                    .append(" 名称=").append(feature.name())
                    .append(" 类型=").append(feature.type())
                    .append(" 经纬度范围=").append(bounds(feature.geojson()))
                    .append('\n');
        }
        return out.toString();
    }

    static String bounds(String geojson) {
        try {
            var node = JSON.readTree(geojson);
            if (node.has("geometry")) node = node.get("geometry");
            double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            accumulate(node.get("coordinates"), box);
            if (!Double.isFinite(box[0])) return "未知";
            return "西=" + box[0] + " 南=" + box[1] + " 东=" + box[2] + " 北=" + box[3];
        } catch (Exception ex) { return "未知"; }
    }

    private static void accumulate(JsonNode node, double[] box) {
        if (node == null || !node.isArray()) return;
        if (node.size() >= 2 && node.get(0).isNumber() && node.get(1).isNumber()) {
            double lon = node.get(0).doubleValue(), lat = node.get(1).doubleValue();
            if (!Double.isFinite(lon) || !Double.isFinite(lat) || Math.abs(lon) > 180 || Math.abs(lat) > 90) return;
            box[0] = Math.min(box[0], lon); box[1] = Math.min(box[1], lat);
            box[2] = Math.max(box[2], lon); box[3] = Math.max(box[3], lat);
        } else node.forEach(child -> accumulate(child, box));
    }

    private static String textArg(JsonNode arguments, String field) {
        if (arguments == null || arguments.get(field) == null || arguments.get(field).isNull()) {
            return "";
        }
        return arguments.get(field).asString().trim();
    }
}
