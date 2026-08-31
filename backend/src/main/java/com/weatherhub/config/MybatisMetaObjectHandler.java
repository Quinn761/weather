package com.weatherhub.config; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler; // 导入 com.baomidou.mybatisplus.core.handlers.MetaObjectHandler 类型或包供本文件使用
import org.apache.ibatis.reflection.MetaObject; // 导入 org.apache.ibatis.reflection.MetaObject 类型或包供本文件使用
import org.springframework.stereotype.Component; // 导入 org.springframework.stereotype.Component 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 java.time.LocalDateTime 类型或包供本文件使用

@Component // 应用 Component 注解配置当前声明
public class MybatisMetaObjectHandler implements MetaObjectHandler { // 声明 MybatisMetaObjectHandler 类

    @Override // 应用 Override 注解配置当前声明
    public void insertFill(MetaObject metaObject) { // 定义 insertFill 方法的入口
        LocalDateTime now = LocalDateTime.now(); // 计算并保存 now 的值
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now); // 执行 strictInsertFill 语句完成当前步骤
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now); // 执行 strictInsertFill 语句完成当前步骤
    } // 

    @Override // 应用 Override 注解配置当前声明
    public void updateFill(MetaObject metaObject) { // 定义 updateFill 方法的入口
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject); // 执行 setFieldValByName 语句完成当前步骤
    } // 
} // 
