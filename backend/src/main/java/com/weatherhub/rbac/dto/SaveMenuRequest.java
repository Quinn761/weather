package com.weatherhub.rbac.dto; // 声明当前 Java 文件所属的包路径

import jakarta.validation.constraints.NotBlank; // 导入 NotBlank 类型或包供本文件使用
import jakarta.validation.constraints.Pattern; // 导入 Pattern 类型或包供本文件使用
import jakarta.validation.constraints.Size; // 导入 Size 类型或包供本文件使用

public record SaveMenuRequest( // 声明 SaveMenuRequest 记录类型及其字段
        Long parentId, // 父菜单主键，空或 0 表示顶级

        @NotBlank(message = "菜单名称不能为空") // 要求字符串参数不能为空白
        @Size(max = 64, message = "菜单名称最长 64 个字符") // 应用 Size 注解配置当前声明
        String name, // 菜单名称

        @Size(max = 128, message = "路由路径最长 128 个字符") // 应用 Size 注解配置当前声明
        String path, // 前端路由，按钮可为空

        @Size(max = 64, message = "图标名最长 64 个字符") // 应用 Size 注解配置当前声明
        String icon, // Element Plus 图标名

        Integer sortNo, // 排序号，越小越靠前

        @Size(max = 64, message = "权限编码最长 64 个字符") // 应用 Size 注解配置当前声明
        String permissionCode, // 接口鉴权使用的权限编码

        @NotBlank(message = "菜单类型不能为空") // 要求字符串参数不能为空白
        @Pattern(regexp = "DIR|MENU|BUTTON", message = "类型只能是 DIR、MENU 或 BUTTON") // 应用 Pattern 注解配置当前声明
        String type, // 菜单类型

        @Pattern(regexp = "ENABLED|DISABLED", message = "状态只能是 ENABLED 或 DISABLED") // 应用 Pattern 注解配置当前声明
        String status // 启用或停用
) { // 开始当前声明或控制结构的代码块
} // 
