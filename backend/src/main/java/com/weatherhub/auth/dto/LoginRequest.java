package com.weatherhub.auth.dto; // 声明当前 Java 文件所属的包路径

import jakarta.validation.constraints.NotBlank; // 导入 NotBlank 类型或包供本文件使用

public record LoginRequest( // 声明 LoginRequest 记录类型及其字段
        @NotBlank(message = "用户名不能为空") // 要求字符串参数不能为空白
        String username, // 声明枚举值或多行参数的一项
        @NotBlank(message = "密码不能为空") // 要求字符串参数不能为空白
        String password // 执行当前 Java 代码行的声明或逻辑
) { // 开始当前声明或控制结构的代码块
} // 
