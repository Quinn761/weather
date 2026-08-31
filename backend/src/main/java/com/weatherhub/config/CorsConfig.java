package com.weatherhub.config; // 声明当前 Java 文件所属的包路径

import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.context.annotation.Bean; // 导入 Bean 类型或包供本文件使用
import org.springframework.context.annotation.Configuration; // 导入 Configuration 类型或包供本文件使用
import org.springframework.web.cors.CorsConfiguration; // 导入 CorsConfiguration 类型或包供本文件使用
import org.springframework.web.cors.CorsConfigurationSource; // 导入 CorsConfigurationSource 类型或包供本文件使用
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // 导入 UrlBasedCorsConfigurationSource 类型或包供本文件使用

import java.util.List; // 导入 java.util.List 类型或包供本文件使用

@Configuration // 声明这是 Spring 配置类
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class CorsConfig { // 声明 CorsConfig 类

    private final CorsProperties corsProperties; // 读取允许的前端来源

    @Bean // 声明该方法返回值注册为 Spring Bean
    public CorsConfigurationSource corsConfigurationSource() { // 给 Spring Security 提供 CORS 规则
        CorsConfiguration config = new CorsConfiguration(); // 创建跨域配置对象
        config.setAllowedOriginPatterns(corsProperties.originList()); // 允许的前端地址，线上用环境变量覆盖
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")); // 允许的 HTTP 方法
        config.setAllowedHeaders(List.of("*")); // 允许携带任意请求头，包括 Authorization
        config.setAllowCredentials(true); // 允许携带凭证
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); // 创建按路径注册的配置源
        source.registerCorsConfiguration("/api/**", config); // 把规则应用到全部接口
        return source; // 返回给安全过滤链使用
    } // 结束当前代码块
} // 结束当前代码块
