package com.weatherhub.config; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.annotation.DbType; // 导入 com.baomidou.mybatisplus.annotation.DbType 类型或包供本文件使用
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor; // 导入 com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor 类型或包供本文件使用
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor; // 导入 com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor 类型或包供本文件使用
import org.mybatis.spring.annotation.MapperScan; // 导入 org.mybatis.spring.annotation.MapperScan 类型或包供本文件使用
import org.springframework.context.annotation.Bean; // 导入 org.springframework.context.annotation.Bean 类型或包供本文件使用
import org.springframework.context.annotation.Configuration; // 导入 org.springframework.context.annotation.Configuration 类型或包供本文件使用

@Configuration // 声明这是 Spring 配置类
@MapperScan({"com.weatherhub.user", "com.weatherhub.rbac", "com.weatherhub.ai.store", "com.weatherhub.ai.kb", "com.weatherhub.camera", "com.weatherhub.operations"}) // 只扫描 MySQL 上的 Mapper，GIS 走独立的 PostGIS 数据源
public class MybatisPlusConfig { // 声明 MybatisPlusConfig 类

    @Bean // 声明该方法返回值注册为 Spring Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() { // 定义 mybatisPlusInterceptor 方法的入口
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); // 计算并保存 interceptor 的值
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); // 执行 addInnerInterceptor 语句完成当前步骤
        return interceptor; // 返回当前方法的处理结果
    } // 
} // 
