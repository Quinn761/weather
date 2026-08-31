package com.weatherhub.rbac.dto; // 声明当前 Java 文件所属的包路径

import com.fasterxml.jackson.annotation.JsonFormat; // 导入 JsonFormat 类型或包供本文件使用
import com.weatherhub.rbac.Role; // 导入 Role 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 LocalDateTime 类型或包供本文件使用
import java.util.List; // 导入 List 类型或包供本文件使用

public record RoleVO( // 声明 RoleVO 记录类型及其字段
        Long id, // 角色主键
        String code, // 角色编码
        String name, // 角色名称
        List<Long> menuIds, // 已绑定的菜单主键
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 应用 JsonFormat 注解配置当前声明
        LocalDateTime createdAt, // 创建时间
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 应用 JsonFormat 注解配置当前声明
        LocalDateTime updatedAt // 更新时间
) { // 开始当前声明或控制结构的代码块
    public static RoleVO from(Role role, List<Long> menuIds) { // 定义 from 方法的入口
        return new RoleVO( // 返回当前方法的处理结果
                role.getId(), // 写入主键
                role.getCode(), // 写入编码
                role.getName(), // 写入名称
                menuIds, // 写入菜单列表
                role.getCreatedAt(), // 写入创建时间
                role.getUpdatedAt() // 写入更新时间
        ); // 结束当前多行语句
    } // 
} // 
