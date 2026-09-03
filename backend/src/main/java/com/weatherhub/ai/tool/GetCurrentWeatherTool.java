package com.weatherhub.ai.tool;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Component
public class GetCurrentWeatherTool implements AiTool {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DEFAULT_CITY = "无锡";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final RestClient http;

    public GetCurrentWeatherTool() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(12));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    GetCurrentWeatherTool(RestClient http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "get_current_weather";
    }

    @Override
    public String description() {
        return "查询某城市实况、预报和空气质量（气温、降水概率、PM2.5、AQI）。问天气、明天下雨、空气质量时必须调用，不要编造。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "城市名，例如无锡。可空则从 query 推断，默认无锡"),
                        "query", Map.of("type", "string", "description", "用户原话，用于抽出城市并判断要实况还是预报")
                )
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        String query = firstText(arguments, "query");
        String city = firstText(arguments, "city");
        if (!StringUtils.hasText(city)) {
            city = extractCity(query);
        }
        try {
            JsonNode place = geocode(city);
            if (place == null) {
                return "没有找到城市「" + city + "」。请改成更明确的地名，例如无锡、上海。";
            }
            String name = text(place.get("name"));
            String admin = text(place.get("admin1"));
            String country = text(place.get("country"));
            double lat = number(place.get("latitude"));
            double lon = number(place.get("longitude"));
            JsonNode root = forecast(lat, lon);
            if (root == null) {
                return "已定位到 " + placeLabel(name, admin, country) + "，但天气接口没有返回数据。";
            }
            StringBuilder out = new StringBuilder();
            out.append("数据来源：Open-Meteo 实况+预报+空气质量，时区 Asia/Shanghai。供质检官分析，不要编造数字。\n");
            out.append("用户问题：").append(StringUtils.hasText(query) ? query : "（未提供）").append('\n');
            out.append("城市：").append(placeLabel(name, admin, country)).append('\n');
            out.append("坐标：").append(lat).append(", ").append(lon).append("\n\n");
            appendCurrent(out, root.get("current"));
            appendDaily(out, root.get("daily"));
            appendHourly(out, root.get("hourly"));
            appendAirQuality(out, airQuality(lat, lon));
            return out.toString();
        } catch (Exception ex) {
            return "查询「" + city + "」天气失败：" + ex.getMessage();
        }
    }

    static String extractCity(String query) {
        if (!StringUtils.hasText(query)) {
            return DEFAULT_CITY;
        }
        String city = query
                .replaceAll("[?？。！!，,、：:]", " ")
                .replaceAll("现在|当前|今天|今日|明天|后天|大后天|今晚|周末|实时|天气|气温|温度|湿度|风速|怎么样|如何|多少度|几度|下雨|降雨|预报|查询|看看|告诉我|帮我|请问|会不会|会否|带伞|出门|冷不冷|热不热|空气质量|空气|雾霾|污染|指数", " ")
                .replaceAll("的|吗|呢|啊|呀", " ")
                .trim()
                .replaceAll("\\s+", "");
        return city.isEmpty() ? DEFAULT_CITY : city;
    }

    static String weatherText(int code) {
        if (code == 0) {
            return "晴";
        }
        if (code <= 3) {
            return "多云";
        }
        if (code == 45 || code == 48) {
            return "雾";
        }
        if (code <= 57) {
            return "毛毛雨";
        }
        if (code <= 67) {
            return "雨";
        }
        if (code <= 77) {
            return "雪";
        }
        if (code <= 82) {
            return "阵雨";
        }
        if (code <= 86) {
            return "阵雪";
        }
        if (code >= 95) {
            return "雷暴";
        }
        return "天气代码 " + code;
    }

    private JsonNode geocode(String city) {
        String raw = http.get()
                .uri("https://geocoding-api.open-meteo.com/v1/search?name={name}&count=1&language=zh", city)
                .retrieve()
                .body(String.class);
        JsonNode root = JSON.readTree(raw == null ? "{}" : raw);
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    private JsonNode forecast(double lat, double lon) {
        String raw = http.get()
                .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                        + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation"
                        + "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max"
                        + "&forecast_days=7&timezone=Asia/Shanghai", lat, lon)
                .retrieve()
                .body(String.class);
        JsonNode root = JSON.readTree(raw == null ? "{}" : raw);
        return root == null || root.isNull() ? null : root;
    }

    private JsonNode airQuality(double lat, double lon) {
        try {
            String raw = http.get()
                    .uri("https://air-quality-api.open-meteo.com/v1/air-quality?latitude={lat}&longitude={lon}"
                            + "&current=european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone"
                            + "&hourly=pm2_5,us_aqi,european_aqi"
                            + "&forecast_days=2&timezone=Asia/Shanghai", lat, lon)
                    .retrieve()
                    .body(String.class);
            JsonNode root = JSON.readTree(raw == null ? "{}" : raw);
            return root == null || root.isNull() ? null : root;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void appendAirQuality(StringBuilder out, JsonNode root) {
        out.append("\n【空气质量】\n");
        if (root == null) {
            out.append("空气质量接口暂不可用。\n");
            return;
        }
        JsonNode current = root.get("current");
        if (current == null || current.isNull()) {
            out.append("无实况\n");
            return;
        }
        int usAqi = (int) Math.round(number(current.get("us_aqi")));
        int euAqi = (int) Math.round(number(current.get("european_aqi")));
        out.append("时间：").append(text(current.get("time"))).append('\n');
        out.append("美国AQI：").append(text(current.get("us_aqi"))).append("（").append(aqiText(usAqi)).append("）\n");
        out.append("欧洲AQI：").append(text(current.get("european_aqi"))).append("（").append(euAqiText(euAqi)).append("）\n");
        out.append("PM2.5：").append(text(current.get("pm2_5"))).append(" μg/m³\n");
        out.append("PM10：").append(text(current.get("pm10"))).append(" μg/m³\n");
        out.append("臭氧 O3：").append(text(current.get("ozone"))).append(" μg/m³\n");
        out.append("二氧化氮 NO2：").append(text(current.get("nitrogen_dioxide"))).append(" μg/m³\n");
        out.append("二氧化硫 SO2：").append(text(current.get("sulphur_dioxide"))).append(" μg/m³\n");
        out.append("一氧化碳 CO：").append(text(current.get("carbon_monoxide"))).append(" μg/m³\n");
        out.append("说明：以上为 Open-Meteo 的美国AQI/欧洲AQI，不是中国生态环境部国标AQI。\n");
        JsonNode hourly = root.get("hourly");
        JsonNode times = hourly == null ? null : hourly.get("time");
        if (times != null && times.isArray() && !times.isEmpty()) {
            out.append("未来24小时 PM2.5 / 美国AQI：\n");
            JsonNode pm = hourly.get("pm2_5");
            JsonNode aqi = hourly.get("us_aqi");
            int limit = Math.min(times.size(), 24);
            for (int i = 0; i < limit; i++) {
                int hourAqi = (int) Math.round(number(at(aqi, i)));
                out.append(text(times.get(i)))
                        .append(" PM2.5=").append(text(at(pm, i))).append("μg/m³")
                        .append(" AQI=").append(text(at(aqi, i)))
                        .append(" ").append(aqiText(hourAqi))
                        .append('\n');
            }
        }
    }

    static String aqiText(int usAqi) {
        if (usAqi <= 50) {
            return "优";
        }
        if (usAqi <= 100) {
            return "良";
        }
        if (usAqi <= 150) {
            return "轻度污染";
        }
        if (usAqi <= 200) {
            return "中度污染";
        }
        if (usAqi <= 300) {
            return "重度污染";
        }
        return "严重污染";
    }

    static String euAqiText(int euAqi) {
        if (euAqi <= 20) {
            return "优";
        }
        if (euAqi <= 40) {
            return "良";
        }
        if (euAqi <= 60) {
            return "中";
        }
        if (euAqi <= 80) {
            return "差";
        }
        if (euAqi <= 100) {
            return "很差";
        }
        return "极差";
    }

    private static void appendCurrent(StringBuilder out, JsonNode current) {
        out.append("【实况】\n");
        if (current == null || current.isNull()) {
            out.append("无\n\n");
            return;
        }
        int code = (int) Math.round(number(current.get("weather_code")));
        out.append("时间：").append(text(current.get("time"))).append('\n');
        out.append("天气：").append(weatherText(code)).append('\n');
        out.append("气温：").append(text(current.get("temperature_2m"))).append("℃\n");
        out.append("体感：").append(text(current.get("apparent_temperature"))).append("℃\n");
        out.append("湿度：").append(text(current.get("relative_humidity_2m"))).append("%\n");
        out.append("风速：").append(text(current.get("wind_speed_10m"))).append(" km/h\n");
        out.append("降水：").append(text(current.get("precipitation"))).append(" mm\n\n");
    }

    private static void appendDaily(StringBuilder out, JsonNode daily) {
        out.append("【未来7天】\n");
        JsonNode times = daily == null ? null : daily.get("time");
        if (times == null || !times.isArray() || times.isEmpty()) {
            out.append("无\n\n");
            return;
        }
        String tomorrow = LocalDate.now(ZONE).plusDays(1).toString();
        JsonNode max = daily.get("temperature_2m_max");
        JsonNode min = daily.get("temperature_2m_min");
        JsonNode code = daily.get("weather_code");
        JsonNode rain = daily.get("precipitation_sum");
        JsonNode prob = daily.get("precipitation_probability_max");
        for (int i = 0; i < times.size(); i++) {
            String day = text(times.get(i));
            int weatherCode = (int) Math.round(number(at(code, i)));
            out.append(day.equals(tomorrow) ? "明天 " : "")
                    .append(day)
                    .append(" 天气=").append(weatherText(weatherCode))
                    .append(" 最高=").append(text(at(max, i))).append("℃")
                    .append(" 最低=").append(text(at(min, i))).append("℃")
                    .append(" 降水概率=").append(text(at(prob, i))).append("%")
                    .append(" 降水量=").append(text(at(rain, i))).append("mm\n");
        }
        out.append('\n');
    }

    private static void appendHourly(StringBuilder out, JsonNode hourly) {
        out.append("【未来48小时】\n");
        JsonNode times = hourly == null ? null : hourly.get("time");
        if (times == null || !times.isArray() || times.isEmpty()) {
            out.append("无\n");
            return;
        }
        JsonNode temp = hourly.get("temperature_2m");
        JsonNode prob = hourly.get("precipitation_probability");
        JsonNode rain = hourly.get("precipitation");
        JsonNode code = hourly.get("weather_code");
        int limit = Math.min(times.size(), 48);
        for (int i = 0; i < limit; i++) {
            int weatherCode = (int) Math.round(number(at(code, i)));
            out.append(text(times.get(i)))
                    .append(" ")
                    .append(text(at(temp, i))).append("℃")
                    .append(" 降水概率=").append(text(at(prob, i))).append("%")
                    .append(" 降水=").append(text(at(rain, i))).append("mm")
                    .append(" ").append(weatherText(weatherCode))
                    .append('\n');
        }
    }

    private static String placeLabel(String name, String admin, String country) {
        StringBuilder out = new StringBuilder(name);
        if (StringUtils.hasText(admin) && !admin.equals(name)) {
            out.append("（").append(admin);
            if (StringUtils.hasText(country)) {
                out.append("，").append(country);
            }
            out.append("）");
        } else if (StringUtils.hasText(country)) {
            out.append("（").append(country).append("）");
        }
        return out.toString();
    }

    private static JsonNode at(JsonNode array, int index) {
        if (array == null || !array.isArray() || index >= array.size()) {
            return null;
        }
        return array.get(index);
    }

    private static String firstText(JsonNode arguments, String field) {
        if (arguments == null || arguments.get(field) == null || arguments.get(field).isNull()) {
            return "";
        }
        return arguments.get(field).asString().trim();
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "-";
        }
        return node.asString();
    }

    private static double number(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        try {
            return Double.parseDouble(node.asString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
