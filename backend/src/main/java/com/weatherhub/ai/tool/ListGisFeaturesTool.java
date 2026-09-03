package com.weatherhub.ai.tool;

import com.weatherhub.gis.GisFeatureService;
import com.weatherhub.gis.dto.GisFeatureVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Component
public class ListGisFeaturesTool implements AiTool {

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
        return "查询当前 PostGIS 中的 GIS 标注。可按名称关键词过滤。问有哪些点、圈、面时必须调用。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "按标注名称过滤，可空")
                )
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        if (gisFeatureService == null) {
            return "GIS 未启用。";
        }
        String keyword = textArg(arguments, "keyword");
        List<GisFeatureVO> features = gisFeatureService.list();
        List<GisFeatureVO> matched = features.stream()
                .filter(item -> !StringUtils.hasText(keyword) || (item.name() != null && item.name().contains(keyword)))
                .toList();
        if (matched.isEmpty()) {
            return features.isEmpty() ? "当前没有任何 GIS 标注。" : "没有名称包含「" + keyword + "」的标注。";
        }
        StringBuilder out = new StringBuilder("共 " + matched.size() + " 条标注：\n");
        for (GisFeatureVO feature : matched) {
            out.append("- id=").append(feature.id())
                    .append(" 名称=").append(feature.name())
                    .append(" 类型=").append(feature.type())
                    .append('\n');
        }
        return out.toString();
    }

    private static String textArg(JsonNode arguments, String field) {
        if (arguments == null || arguments.get(field) == null || arguments.get(field).isNull()) {
            return "";
        }
        return arguments.get(field).asString().trim();
    }
}
