package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 导入 BaseMapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Mapper; // 导入 Mapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Select; // 导入 Select 类型或包供本文件使用

import java.util.List; // 导入 List 类型或包供本文件使用

@Mapper // 应用 Mapper 注解配置当前声明
public interface MenuMapper extends BaseMapper<Menu> { // 声明 MenuMapper 接口

    @Select("SELECT DISTINCT m.permission_code FROM sys_menu m INNER JOIN sys_role_menu rm ON rm.menu_id = m.id INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id WHERE ur.user_id = #{userId} AND m.status = 'ENABLED' AND m.permission_code IS NOT NULL AND m.permission_code <> ''") // 按用户汇总菜单上的权限编码
    List<String> selectCodesByUserId(Long userId); // 定义 selectCodesByUserId 方法的入口

    @Select("SELECT DISTINCT m.id, m.parent_id, m.name, m.path, m.icon, m.sort_no, m.permission_code, m.type, m.status, m.created_at, m.updated_at FROM sys_menu m INNER JOIN sys_role_menu rm ON rm.menu_id = m.id INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id WHERE ur.user_id = #{userId} AND m.status = 'ENABLED' ORDER BY m.sort_no ASC, m.id ASC") // 按用户查询已授权且启用的菜单
    List<Menu> selectByUserId(Long userId); // 定义 selectByUserId 方法的入口
} // 
