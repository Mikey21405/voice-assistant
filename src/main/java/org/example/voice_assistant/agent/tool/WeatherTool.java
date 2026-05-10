package org.example.voice_assistant.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.agent.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeatherTool implements Tool {

    private final WebClient webClient;

    @Value("${qweather.api.key}")
    private String apiKey;

    @Value("${qweather.geo.url}")
    private String geoApiUrl;

    @Value("${qweather.weather.url}")
    private String weatherApiUrl;

    public WeatherTool(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "获取指定城市实时天气";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> scheme = new HashMap<>();
        scheme.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> cityProperty = new HashMap<>();
        cityProperty.put("type", "String");
        cityProperty.put("description", "城市名称，例如：北京、上海、杭州");
        properties.put("city", cityProperty);

        scheme.put("properties", properties);
        scheme.put("required", List.of("city"));
        return scheme;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String city = (String) arguments.get("city");
        log.info("WeatherTool executed: city={}", city);

        try {
            String locationId = lookupCity(city);
            if (locationId == null) {
                return "未找到城市「" + city + "」的信息，请检查城市名称是否正确";
            }

            return fetchWeather(city, locationId);
        } catch (Exception e) {
            log.error("WeatherTool error for city={}", city, e);
            return "获取天气信息失败: " + e.getMessage();
        }
    }

    private String lookupCity(String city) {
        URI uri = UriComponentsBuilder.fromHttpUrl(geoApiUrl)
                .queryParam("location", city)
                .queryParam("key", apiKey)
                .encode()
                .build()
                .toUri();
        log.info("调用地理编码API，uri={}", uri);

        String response = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("地理编码API响应：{}", response);

        JSONObject json = JSON.parseObject(response);
        String code = json.getString("code");
        log.info("code:{}", code);
        if (!"200".equals(code)) {
            log.warn("调用地理编码API失败，code={}", code);
            return null;
        }

        JSONArray locations = json.getJSONArray("location");
        if (locations == null || locations.isEmpty()) {
            log.warn("未找到城市「{}」的信息", city);
            return null;
        }

        JSONObject location = locations.getJSONObject(0);
        String locationId = location.getString("id");
        String name = location.getString("name");
        log.info("找到城市「{}」，locationId={}", name, locationId);

        return locationId;
    }

    private String fetchWeather(String city, String locationId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(weatherApiUrl)
                .queryParam("location", locationId)
                .encode()
                .build()
                .toUri();
        log.debug("调用天气API，uri={}", uri);

        String response = webClient.get()
                .uri(uri)
                .header("X-QW-Api-Key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.debug("天气API响应：{}", response);

        JSONObject json = JSON.parseObject(response);
        String code = json.getString("code");

        if (!"200".equals(code)) {
            log.warn("调用天气API失败，code={}", code);
            return "获取天气数据失败，请稍后重试,错误代码：" + code;
        }

        JSONObject now = json.getJSONObject("now");
        if (now == null) {
            return "天气数据暂不可用";
        }

        String text = now.getString("text");
        String temp = now.getString("temp");
        String feelsLike = now.getString("feelsLike");
        String humidity = now.getString("humidity");
        String windDir = now.getString("windDir");
        String windScale = now.getString("windScale");
        String precip = now.getString("precip");
        String vis = now.getString("vis");

        StringBuilder sb = new StringBuilder();
        sb.append("城市 ").append(city).append("，");
        sb.append("天气").append(text).append("，");
        sb.append("温度").append(temp).append("°C，");
        sb.append("体感温度").append(feelsLike).append("°C，");
        sb.append("湿度").append(humidity).append("%，");
        sb.append("风向").append(windDir).append("，");
        sb.append("风力").append(windScale).append("级");

        if (precip != null && !"0.0".equals(precip)) {
            sb.append("，降水量").append(precip).append("mm");
        }
        if (vis != null && !vis.isEmpty()) {
            sb.append("，能见度").append(vis).append("km");
        }

        return sb.toString();
    }
}
