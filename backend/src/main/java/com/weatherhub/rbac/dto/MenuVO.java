package com.weatherhub.rbac.dto; // 声明当前 Java 文件所属的包路径

import com.fasterxml.jackson.annotation.JsonFormat; // 导入 JsonFormat 类型或包供本文件使用
import com.weatherhub.rbac.Menu; // 导入 Menu 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 LocalDateTime 类型或包供本文件使用
import java.util.ArrayList; // 导入 ArrayList 类型或包供本文件使用
import java.util.List; // 导入 List 类型或包供本文件使用

public record MenuVO( // 声明 MenuVO 记录类型及其字段
        Long id, // 菜单主键
        Long parentId, // 父菜单主键，0 表示顶级
        String name, // 菜单名称
        String path, // 前端路由
        String icon, // 图标名
        Integer sortNo, // 排序号
        String permissionCode, // 权限编码
        String type, // DIR / MENU / BUTTON
        String status, // ENABLED / DISABLED
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 应用 JsonFormat 注解配置当前声明
        LocalDateTime createdAt, // 创建时间
        List<MenuVO> children // 子菜单，用于树形展示
) { // 开始当前声明或控制结构的代码块
    public static MenuVO from(Menu menu) { // 定义 from 方法的入口
        return new MenuVO( // 返回当前方法的处理结果
                menu.getId(), // 写入主键
                menu.getParentId(), // 写入父级
                menu.getName(), // 写入名称
                menu.getPath(), // 写入路由
                menu.getIcon(), // 写入图标
                menu.getSortNo(), // 写入排序
                menu.getPermissionCode(), // 写入权限编码
                menu.getType(), // 写入类型
                menu.getStatus(), // 写入状态
                menu.getCreatedAt(), // 写入创建时间
                new ArrayList<>() // 预留子节点列表
        ); // 结束当前多行语句
    } // 
} // 
