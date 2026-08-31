package com.weatherhub.auth.dto; // 声明当前 Java 文件所属的包路径

import com.weatherhub.user.dto.UserVO; // 导入 UserVO 类型或包供本文件使用

public record LoginVO( // 声明 LoginVO 记录类型及其字段
        String token, // 声明枚举值或多行参数的一项
        String tokenType, // 声明枚举值或多行参数的一项
        long expiresIn, // 声明枚举值或多行参数的一项
        UserVO user // 执行当前 Java 代码行的声明或逻辑
) { // 开始当前声明或控制结构的代码块
} // 
