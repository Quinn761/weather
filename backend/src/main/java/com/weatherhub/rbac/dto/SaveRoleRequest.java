package com.weatherhub.rbac.dto; // 声明当前 Java 文件所属的包路径

import jakarta.validation.constraints.NotBlank; // 导入 NotBlank 类型或包供本文件使用
import jakarta.validation.constraints.Size; // 导入 Size 类型或包供本文件使用

import java.util.List; // 导入 List 类型或包供本文件使用

public record SaveRoleRequest( // 声明 SaveRoleRequest 记录类型及其字段
        @NotBlank(message = "角色编码不能为空") // 要求字符串参数不能为空白
        @Size(max = 64, message = "角色编码最长 64 个字符") // 应用 Size 注解配置当前声明
        String code, // 角色编码，创建后不可改

        @NotBlank(message = "角色名称不能为空") // 要求字符串参数不能为空白
        @Size(max = 64, message = "角色名称最长 64 个字符") // 应用 Size 注解配置当前声明
        String name, // 角色显示名

        List<Long> menuIds // 要绑定的菜单主键列表
) { // 开始当前声明或控制结构的代码块
} // 
