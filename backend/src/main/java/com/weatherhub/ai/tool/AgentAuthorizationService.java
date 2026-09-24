package com.weatherhub.ai.tool;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The policy layer. Model output never participates in this decision. */
@Service
public class AgentAuthorizationService {
    private static final Map<String, ToolPolicy> POLICIES = Map.ofEntries(
            Map.entry("get_current_weather", new ToolPolicy(ToolOperation.READ, List.of())),
            Map.entry("get_official_alerts", new ToolPolicy(ToolOperation.READ, List.of("official-alert:read"))),
            Map.entry("get_current_user", new ToolPolicy(ToolOperation.READ, List.of())),
            Map.entry("get_dashboard_overview", new ToolPolicy(ToolOperation.READ, List.of("dashboard:view"))),
            Map.entry("list_gis_features", new ToolPolicy(ToolOperation.READ, List.of("gis:read"))),
            Map.entry("search_knowledge", new ToolPolicy(ToolOperation.READ, List.of("kb:read"))),
            Map.entry("get_system_data", new ToolPolicy(ToolOperation.READ, List.of("dashboard:view"))),
            Map.entry("delete_user", new ToolPolicy(ToolOperation.WRITE, List.of("user:write"))),
            Map.entry("update_user", new ToolPolicy(ToolOperation.WRITE, List.of("user:write"))),
            Map.entry("delete_gis_feature", new ToolPolicy(ToolOperation.WRITE, List.of("gis:write"))),
            Map.entry("publish_alert", new ToolPolicy(ToolOperation.PUBLISH, List.of()))
    );

    public ToolAuthorizationDecision authorize(String tool, Authentication authentication) {
        ToolPolicy policy = POLICIES.get(tool);
        if (policy == null) return ToolAuthorizationDecision.deny("未注册到权限策略的工具，拒绝执行：" + tool);
        if (policy.operation() != ToolOperation.READ) {
            return ToolAuthorizationDecision.deny("自动 Agent 不执行" + operationName(policy.operation()) + "操作；必须进入人工审批工作流");
        }
        if (authentication == null || !authentication.isAuthenticated()) return ToolAuthorizationDecision.deny("未登录，拒绝执行工具");
        Set<String> authorities = authentication.getAuthorities().stream().map(item -> item.getAuthority()).collect(Collectors.toSet());
        for (String required : policy.requiredAuthorities()) {
            if (!authorities.contains(required)) return ToolAuthorizationDecision.deny("缺少权限：" + required);
        }
        return ToolAuthorizationDecision.allow();
    }

    private static String operationName(ToolOperation operation) {
        return operation == ToolOperation.WRITE ? "写入/删除/修改" : "发布";
    }

    private record ToolPolicy(ToolOperation operation, List<String> requiredAuthorities) { }
}
