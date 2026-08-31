package com.weatherhub.dashboard.dto; // 声明当前 Java 文件所属的包路径

public record OverviewVO( // 声明 OverviewVO 记录类型及其字段
        long userCount, // 系统用户数
        long roleCount, // 角色数
        long menuCount // 菜单数
) { // 开始当前声明或控制结构的代码块
} // 
