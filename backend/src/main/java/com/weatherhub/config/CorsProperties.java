package com.weatherhub.config; // 声明当前 Java 文件所属的包路径

import lombok.Getter; // 导入 Getter 类型或包供本文件使用
import lombok.Setter; // 导入 Setter 类型或包供本文件使用
import org.springframework.boot.context.properties.ConfigurationProperties; // 导入 ConfigurationProperties 类型或包供本文件使用

import java.util.Arrays; // 导入 Arrays 类型或包供本文件使用
import java.util.List; // 导入 List 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@ConfigurationProperties(prefix = "weatherhub.cors") // 把 weatherhub.cors 配置绑定到当前类
public class CorsProperties { // 声明 CorsProperties 类

    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173"; // 允许的前端来源，逗号分隔

    public List<String> originList() {
        List<String> configured = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        java.util.LinkedHashSet<String> origins = new java.util.LinkedHashSet<>(configured);
        origins.add("http://localhost:5173");
        origins.add("http://127.0.0.1:5173");
        return List.copyOf(origins);
    } // 结束当前代码块
} // 结束当前代码块
