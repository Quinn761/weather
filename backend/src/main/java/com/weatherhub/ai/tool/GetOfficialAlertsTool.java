package com.weatherhub.ai.tool;

import com.weatherhub.officialalert.OfficialAlertService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only access to the official warning feed used by the alert page. */
@Component
public class GetOfficialAlertsTool implements AiTool {
    private final OfficialAlertService officialAlerts;

    public GetOfficialAlertsTool(OfficialAlertService officialAlerts) {
        this.officialAlerts = officialAlerts;
    }

    @Override
    public String name() { return "get_official_alerts"; }

    @Override
    public String description() { return "查询当前官方灾害预警，只读，不会执行发布或处置操作。"; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "query", Map.of("type", "string", "description", "用户的预警查询问题")
        ));
    }

    @Override
    public String execute(JsonNode arguments) {
        String query = arguments == null ? "" : arguments.path("query").asString("").trim();
        var overview = officialAlerts.overview();
        if (!overview.configured()) return "官方灾害预警服务尚未配置和风天气 API 密钥。";

        List<String> warnings = new ArrayList<>();
        for (var region : overview.regions()) {
            for (var warning : region.warnings()) {
                String searchable = String.join(" ", region.regionName(), warning.title(), warning.typeName(), warning.level(), warning.sender(), warning.text());
                if (!matches(searchable, query)) continue;
                String detail = StringUtils.hasText(warning.text()) ? "：" + warning.text() : "";
                warnings.add(region.regionName() + " | " + warning.title() + " | " + warning.level() + " | " + warning.pubTime() + detail);
            }
        }
        if (warnings.isEmpty()) return "当前未找到匹配的有效官方灾害预警。";
        int limit = 20;
        String suffix = warnings.size() > limit ? "；其余 " + (warnings.size() - limit) + " 条未展开" : "";
        return "当前查询到 " + warnings.size() + " 条官方有效预警：\n- "
                + String.join("\n- ", warnings.subList(0, Math.min(limit, warnings.size()))) + suffix;
    }

    private static boolean matches(String searchable, String query) {
        if (!StringUtils.hasText(query)) return true;
        String normalized = searchable.toLowerCase(Locale.ROOT);
        for (String term : query.toLowerCase(Locale.ROOT).split("[，。,；;、\\s]+")) {
            if (term.length() >= 2 && normalized.contains(term)) return true;
        }
        return query.contains("全部") || query.contains("哪些") || query.contains("当前") || query.contains("现在");
    }
}
