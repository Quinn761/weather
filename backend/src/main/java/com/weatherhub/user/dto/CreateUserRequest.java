package com.weatherhub.user.dto; // 声明当前 Java 文件所属的包路径

import jakarta.validation.constraints.NotBlank; // 导入 jakarta.validation.constraints.NotBlank 类型或包供本文件使用
import jakarta.validation.constraints.Pattern; // 导入 jakarta.validation.constraints.Pattern 类型或包供本文件使用
import jakarta.validation.constraints.Size; // 导入 jakarta.validation.constraints.Size 类型或包供本文件使用

public record CreateUserRequest( // 声明 CreateUserRequest 记录类型及其字段
        @NotBlank(message = "用户名不能为空") // 要求字符串参数不能为空白
        @Size(max = 64, message = "用户名最长 64 个字符") // 应用 Size 注解配置当前声明
        String username, // 声明枚举值或多行参数的一项

        @NotBlank(message = "显示名不能为空") // 要求字符串参数不能为空白
        @Size(max = 64, message = "显示名最长 64 个字符") // 应用 Size 注解配置当前声明
        String nickname, // 声明枚举值或多行参数的一项

        @Size(max = 128, message = "邮箱最长 128 个字符") // 应用 Size 注解配置当前声明
        String email, // 声明枚举值或多行参数的一项

        @Size(max = 32, message = "手机号最长 32 个字符") // 应用 Size 注解配置当前声明
        String phone, // 声明枚举值或多行参数的一项

        @NotBlank(message = "密码不能为空") // 要求新建用户必须设置密码
        @Size(min = 6, max = 64, message = "密码长度需要 6 到 64 个字符") // 限制密码长度
        String password, // 声明新建用户的明文密码

        @Pattern(regexp = "ENABLED|DISABLED", message = "状态只能是 ENABLED 或 DISABLED") // 应用 Pattern 注解配置当前声明
        String status, // 执行当前 Java 代码行的声明或逻辑

        java.util.List<Long> roleIds // 可选，指定后覆盖默认 USER 角色
) { // 开始当前声明或控制结构的代码块
} // 
