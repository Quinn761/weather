package com.weatherhub.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicPlannerTest {

    @Test
    void plansGisAndReview() {
        List<PlanStep> steps = HeuristicPlanner.plan("GIS 里有哪些标注？");
        assertTrue(steps.stream().anyMatch(step -> "planner".equals(step.agent())));
        assertTrue(steps.stream().anyMatch(step -> "list_gis_features".equals(step.tool())));
        assertTrue(steps.stream().anyMatch(step -> "reviewer".equals(step.agent())));
    }

    @Test
    void plansDashboard() {
        List<PlanStep> steps = HeuristicPlanner.plan("现在系统有多少用户？");
        assertTrue(steps.stream().anyMatch(step -> "get_dashboard_overview".equals(step.tool())));
    }

    @Test
    void plansKnowledgeForJwt() {
        List<PlanStep> steps = HeuristicPlanner.plan("JWT 退出黑名单怎么做的");
        assertTrue(steps.stream().anyMatch(step -> "knowledge".equals(step.agent())));
    }

    @Test
    void plansWeatherForTomorrowRain() {
        List<PlanStep> steps = HeuristicPlanner.plan("无锡明天会不会下雨？");
        assertTrue(steps.stream().anyMatch(step -> "get_current_weather".equals(step.tool())));
        assertTrue(steps.stream().noneMatch(step -> "knowledge".equals(step.agent())));
    }

    @Test
    void plansAirQualityForShanghai() {
        List<PlanStep> steps = HeuristicPlanner.plan("上海空气质量怎么样");
        assertTrue(steps.stream().anyMatch(step -> "get_current_weather".equals(step.tool())));
        assertTrue(steps.stream().noneMatch(step -> "knowledge".equals(step.agent())));
    }
}
