package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.annotation.TableName; // 导入 TableName 类型或包供本文件使用
import lombok.Getter; // 导入 lombok.Getter 类型或包供本文件使用
import lombok.Setter; // 导入 lombok.Setter 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@TableName("sys_user_role") // 应用 TableName 注解配置当前声明
public class UserRole { // 声明 UserRole 类

    private Long userId; // 定义 userId 字段保存对象状态或依赖

    private Long roleId; // 定义 roleId 字段保存对象状态或依赖
} // 
