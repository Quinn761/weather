package com.weatherhub.ai.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GetCurrentWeatherToolTest {

    @Test
    void preservesProvinceForProvinceWeatherQuery() {
        assertEquals("\u6d77\u5357\u7701", GetCurrentWeatherTool.extractCity("\u6d77\u5357\u7701\u5929\u6c14"));
    }

    @Test
    void extractsCityFromSevenDayForecastQuery() {
        assertEquals("\u6d77\u53e3", GetCurrentWeatherTool.extractCity("\u6d77\u53e3\u672a\u67657\u5929\u5929\u6c14"));
    }

    @Test
    void extractsWuxiFromTomorrowRain() {
        assertEquals("无锡", GetCurrentWeatherTool.extractCity("无锡明天会不会下雨？"));
    }

    @Test
    void defaultsToWuxiWhenNoCity() {
        assertEquals("无锡", GetCurrentWeatherTool.extractCity("现在天气怎么样"));
    }

    @Test
    void extractsShanghaiFromAirQuality() {
        assertEquals("上海", GetCurrentWeatherTool.extractCity("上海空气质量怎么样"));
    }

    @Test
    void mapsAqi() {
        assertEquals("优", GetCurrentWeatherTool.aqiText(32));
        assertEquals("轻度污染", GetCurrentWeatherTool.aqiText(120));
    }

    @Test
    void mapsClearSky() {
        assertEquals("晴", GetCurrentWeatherTool.weatherText(0));
        assertEquals("雨", GetCurrentWeatherTool.weatherText(61));
    }
}
