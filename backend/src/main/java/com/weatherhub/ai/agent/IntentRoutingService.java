package com.weatherhub.ai.agent;

import org.springframework.stereotype.Service;

import java.util.Locale;

/** The intent layer: classify only. It cannot select or execute a tool. */
@Service
public class IntentRoutingService {
    private final JevDecisionClient jev;

    public IntentRoutingService(JevDecisionClient jev) {
        this.jev = jev;
    }

    public IntentDecision classify(String question) {
        return jev.classify(question).orElseGet(() -> new IntentDecision(heuristic(question), "local", 0));
    }

    private static String heuristic(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (contains(text, "我是谁", "我的权限", "当前用户", "当前登录", "who am i")) return "current_user";
        if (contains(text, "多少用户", "用户数", "角色数", "菜单数", "概览", "工作台", "系统规模")) return "dashboard";
        if (contains(text, "gis", "标注", "圈地", "地块", "postgis")) return "gis";
        if (contains(text, "天气", "气温", "温度", "湿度", "下雨", "降雨", "预报", "空气质量", "pm2", "pm10", "aqi")) return "weather";
        if (contains(text, "jwt", "rbac", "权限", "黑名单", "redis", "mcp", "rag", "agent", "知识库", "系统原理", "系统架构", "本项目", "当前系统", "功能", "部署", "sam", "roboflow", "spring", "security")) return "system_knowledge";
        if (contains(text, "\u9884\u8b66", "\u8b66\u62a5", "\u707e\u5bb3", "\u53f0\u98ce", "alert", "warning")) return "official_alert";
        return "chat";
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }
}
