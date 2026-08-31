package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.annotation.TableName; // 导入 TableName 类型或包供本文件使用
import lombok.Getter; // 导入 Getter 类型或包供本文件使用
import lombok.Setter; // 导入 Setter 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@TableName("sys_role_menu") // 应用 TableName 注解配置当前声明
public class RoleMenu { // 声明 RoleMenu 类

    private Long roleId; // 定义 roleId 字段保存对象状态或依赖

    private Long menuId; // 定义 menuId 字段保存对象状态或依赖
} // 
