package com.weatherhub.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HeuristicPlanner {

    private HeuristicPlanner() {
    }

    public static List<PlanStep> plan(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<PlanStep> steps = new ArrayList<>();
        steps.add(new PlanStep("planner", "拆解目标，分配规划官 / 知识官 / 执行官 / 质检官", null));

        boolean me = contains(text, "我是谁", "我的权限", "当前用户", "当前登录", "who am i");
        boolean data = contains(text, "多少用户", "用户数", "角色数", "菜单数", "概览", "工作台", "系统规模", "有多少");
        boolean gis = contains(text, "gis", "标注", "打点", "圈地", "点位", "postgis");
        boolean weather = contains(text, "天气", "气温", "温度", "湿度", "风速", "下雨", "降雨", "几度", "多少度",
                "天气预报", "明天", "后天", "会不会", "带伞", "冷不冷", "热不热", "出门",
                "空气", "空气质量", "雾霾", "pm2", "pm10", "aqi", "污染");
        boolean know = contains(text, "jwt", "rbac", "权限", "黑名单", "redis", "mcp", "rag", "agent", "双数据源", "事务",
                "原理", "怎么实现", "怎么做", "如何", "链路", "spring", "security") && !weather;

        if (me) {
            steps.add(new PlanStep("operator", "执行官查询当前登录用户和权限", "get_current_user"));
        }
        if (data) {
            steps.add(new PlanStep("operator", "执行官查询工作台统计", "get_dashboard_overview"));
        }
        if (gis) {
            steps.add(new PlanStep("operator", "执行官查询 GIS 标注", "list_gis_features"));
        }
        if (weather) {
            steps.add(new PlanStep("operator", "执行官调用 Open-Meteo 查询天气和空气质量", "get_current_weather"));
        }
        if (know || (!me && !data && !gis && !weather)) {
            steps.add(new PlanStep("knowledge", "知识官检索知识库", "search_knowledge"));
        }
        steps.add(new PlanStep("reviewer", "质检官汇总证据并给出最终回答", null));
        return steps;
    }

    private static boolean contains(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
