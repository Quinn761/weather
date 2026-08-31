package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.annotation.FieldFill; // 导入 FieldFill 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.IdType; // 导入 IdType 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableField; // 导入 TableField 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableId; // 导入 TableId 类型或包供本文件使用
import com.baomidou.mybatisplus.annotation.TableName; // 导入 TableName 类型或包供本文件使用
import lombok.Getter; // 导入 Getter 类型或包供本文件使用
import lombok.Setter; // 导入 Setter 类型或包供本文件使用

import java.time.LocalDateTime; // 导入 LocalDateTime 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
@Setter // 应用 Setter 注解配置当前声明
@TableName("sys_menu") // 应用 TableName 注解配置当前声明
public class Menu { // 声明 Menu 类

    public static final String TYPE_DIR = "DIR"; // 目录类型，只用于分组
    public static final String TYPE_MENU = "MENU"; // 页面菜单，会出现在侧栏
    public static final String TYPE_BUTTON = "BUTTON"; // 按钮权限，不出现在侧栏
    public static final String STATUS_ENABLED = "ENABLED"; // 启用状态

    @TableId(type = IdType.AUTO) // 应用 TableId 注解配置当前声明
    private Long id; // 定义 id 字段保存对象状态或依赖

    private Long parentId; // 定义 parentId 字段，0 表示顶级菜单

    private String name; // 定义 name 字段保存菜单名称

    private String path; // 定义 path 字段保存前端路由路径

    private String icon; // 定义 icon 字段保存 Element Plus 图标名

    private Integer sortNo; // 定义 sortNo 字段保存排序号

    private String permissionCode; // 定义 permissionCode 字段保存接口权限编码

    private String type; // 定义 type 字段保存 DIR / MENU / BUTTON

    private String status; // 定义 status 字段保存启用或停用

    @TableField(fill = FieldFill.INSERT) // 应用 TableField 注解配置当前声明
    private LocalDateTime createdAt; // 定义 createdAt 字段保存对象状态或依赖

    @TableField(fill = FieldFill.INSERT_UPDATE) // 应用 TableField 注解配置当前声明
    private LocalDateTime updatedAt; // 定义 updatedAt 字段保存对象状态或依赖
} // 
