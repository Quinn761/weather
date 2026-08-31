package com.weatherhub.user.dto; // 声明当前 Java 文件所属的包路径

import com.fasterxml.jackson.annotation.JsonFormat; // 导入 JsonFormat 类型或包供本文件使用
import com.weatherhub.rbac.dto.MenuVO; // 导入 MenuVO 类型或包供本文件使用
import com.weatherhub.user.User; // 导入 User 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 LocalDateTime 类型或包供本文件使用
import java.util.List; // 导入 List 类型或包供本文件使用

public record UserVO( // 声明 UserVO 记录类型及其字段
        Long id, // 声明枚举值或多行参数的一项
        String username, // 声明枚举值或多行参数的一项
        String nickname, // 声明枚举值或多行参数的一项
        String email, // 声明枚举值或多行参数的一项
        String phone, // 声明枚举值或多行参数的一项
        String status, // 声明枚举值或多行参数的一项
        List<Long> roleIds, // 已绑定角色主键，供编辑回填
        List<String> roles, // 已绑定角色编码
        List<String> permissions, // 由菜单汇总出的权限编码
        List<MenuVO> menus, // 侧栏菜单树，仅登录态详情需要
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 应用 JsonFormat 注解配置当前声明
        LocalDateTime createdAt, // 声明枚举值或多行参数的一项
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 应用 JsonFormat 注解配置当前声明
        LocalDateTime updatedAt // 执行当前 Java 代码行的声明或逻辑
) { // 开始当前声明或控制结构的代码块
    public static UserVO from(User user, List<Long> roleIds, List<String> roles, List<String> permissions, List<MenuVO> menus) { // 定义 from 方法的入口
        return new UserVO( // 返回当前方法的处理结果
                user.getId(), // 声明枚举值或多行参数的一项
                user.getUsername(), // 声明枚举值或多行参数的一项
                user.getNickname(), // 声明枚举值或多行参数的一项
                user.getEmail(), // 声明枚举值或多行参数的一项
                user.getPhone(), // 声明枚举值或多行参数的一项
                user.getStatus(), // 声明枚举值或多行参数的一项
                roleIds, // 写入角色主键
                roles, // 写入角色编码
                permissions, // 写入权限编码
                menus, // 写入菜单树
                user.getCreatedAt(), // 声明枚举值或多行参数的一项
                user.getUpdatedAt() // 继续填写多行调用的参数
        ); // 结束当前多行语句
    } // 
} // 
