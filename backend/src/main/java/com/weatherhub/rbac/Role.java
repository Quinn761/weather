package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.annotation.FieldFill; // 导入 FieldFill 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.IdType; // 导入 IdType 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableField; // 导入 TableField 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableId; // 导入 TableId 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableName; // 导入 TableName 类型或包供本文件使用
import lombok.Getter; // 导入 lombok.Getter 类型或包供本文件使用
import lombok.Setter; // 导入 lombok.Setter 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 java.time.LocalDateTime 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@TableName("sys_role") // 应用 TableName 注解配置当前声明
public class Role { // 声明 Role 类

    @TableId(type = IdType.AUTO) // 应用 TableId 注解配置当前声明
    private Long id; // 定义 id 字段保存对象状态或依赖

    private String code; // 定义 code 字段保存对象状态或依赖

    private String name; // 定义 name 字段保存对象状态或依赖

    @TableField(fill = FieldFill.INSERT) // 应用 TableField 注解配置当前声明
    private LocalDateTime createdAt; // 定义 createdAt 字段保存对象状态或依赖

    @TableField(fill = FieldFill.INSERT_UPDATE) // 应用 TableField 注解配置当前声明
    private LocalDateTime updatedAt; // 定义 updatedAt 字段保存对象状态或依赖
} // 
