package com.weatherhub.user; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.annotation.FieldFill; // 导入 com.baomidou.mybatisplus.annotation.FieldFill 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.IdType; // 导入 com.baomidou.mybatisplus.annotation.IdType 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableField; // 导入 com.baomidou.mybatisplus.annotation.TableField 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableId; // 导入 com.baomidou.mybatisplus.annotation.TableId 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableName; // 导入 com.baomidou.mybatisplus.annotation.TableName 类型或包供本文件使用
import lombok.Getter; // 导入 lombok.Getter 类型或包供本文件使用
import lombok.Setter; // 导入 lombok.Setter 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 java.time.LocalDateTime 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@TableName("sys_user") // 应用 TableName 注解配置当前声明
public class User { // 声明 User 类

    @TableId(type = IdType.AUTO) // 应用 TableId 注解配置当前声明
    private Long id; // 定义 id 字段保存对象状态或依赖

    private String username; // 定义 username 字段保存对象状态或依赖

    private String nickname; // 定义 nickname 字段保存对象状态或依赖

    private String email; // 定义 email 字段保存对象状态或依赖

    private String phone; // 定义 phone 字段保存对象状态或依赖

    private String password; // 定义 password 字段保存对象状态或依赖

    private String status; // 定义 status 字段保存对象状态或依赖

    @TableField(fill = FieldFill.INSERT) // 应用 TableField 注解配置当前声明
    private LocalDateTime createdAt; // 定义 createdAt 字段保存对象状态或依赖

    @TableField(fill = FieldFill.INSERT_UPDATE) // 应用 TableField 注解配置当前声明
    private LocalDateTime updatedAt; // 定义 updatedAt 字段保存对象状态或依赖
} // 
