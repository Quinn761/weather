package com.weatherhub.config; // 声明当前 Java 文件所属的包路径

import lombok.Getter; // 导入 lombok.Getter 类型或包供本文件使用
import lombok.Setter; // 导入 lombok.Setter 类型或包供本文件使用
import org.springframework.boot.context.properties.ConfigurationProperties; // 导入 ConfigurationProperties 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@ConfigurationProperties(prefix = "weatherhub.jwt") // 把 weatherhub.jwt 配置绑定到当前类
public class JwtProperties { // 声明 JwtProperties 类

    private String secret; // 定义 secret 字段保存对象状态或依赖

    private long expireHours = 24; // 定义 expireHours 字段保存对象状态或依赖
} // 
