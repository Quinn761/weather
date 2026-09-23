package com.weatherhub; // 声明当前 Java 文件所属的包路径

import org.springframework.boot.SpringApplication; // 导入 org.springframework.boot.SpringApplication 类型或包供本文件使用
import org.springframework.boot.autoconfigure.SpringBootApplication; // 导入 SpringBootApplication 类型或包供本文件使用
import org.springframework.boot.context.properties.EnableConfigurationProperties; // 导入 EnableConfigurationProperties 类型或包供本文件使用
import org.springframework.scheduling.annotation.EnableScheduling;
import com.weatherhub.config.AiProperties; // 导入 AiProperties 类型或包供本文件使用
import com.weatherhub.config.JevProperties;
import com.weatherhub.config.CameraCaptureProperties;
import com.weatherhub.config.CorsProperties; // 导入 CorsProperties 类型或包供本文件使用
import com.weatherhub.config.JwtProperties; // 导入 JwtProperties 类型或包供本文件使用

@SpringBootApplication // 标记 Spring Boot 应用入口类
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AiProperties.class, JevProperties.class, CameraCaptureProperties.class}) // 启用 JWT、AI 与相机配置绑定
public class WeatherDataHubApplication { // 声明 WeatherDataHubApplication 类

    public static void main(String[] args) { // 定义 main 方法的入口
        SpringApplication.run(WeatherDataHubApplication.class, args); // 执行 run 语句完成当前步骤
    } // 
} // 
