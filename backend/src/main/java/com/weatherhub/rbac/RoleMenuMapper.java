package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 导入 BaseMapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Delete; // 导入 Delete 类型或包供本文件使用
import org.apache.ibatis.annotations.Insert; // 导入 Insert 类型或包供本文件使用
import org.apache.ibatis.annotations.Mapper; // 导入 Mapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Param; // 导入 Param 类型或包供本文件使用
import org.apache.ibatis.annotations.Select; // 导入 Select 类型或包供本文件使用

import java.util.List; // 导入 List 类型或包供本文件使用

@Mapper // 应用 Mapper 注解配置当前声明
public interface RoleMenuMapper extends BaseMapper<RoleMenu> { // 声明 RoleMenuMapper 接口

    @Insert("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})") // 写入角色菜单关系并忽略重复
    int insertIgnore(@Param("roleId") Long roleId, @Param("menuId") Long menuId); // 定义 insertIgnore 方法的入口

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}") // 按角色删除全部菜单关系
    int deleteByRoleId(@Param("roleId") Long roleId); // 定义 deleteByRoleId 方法的入口

    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}") // 按菜单删除全部角色关系
    int deleteByMenuId(@Param("menuId") Long menuId); // 定义 deleteByMenuId 方法的入口

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}") // 查询角色已绑定的菜单主键
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId); // 定义 selectMenuIdsByRoleId 方法的入口
} // 
