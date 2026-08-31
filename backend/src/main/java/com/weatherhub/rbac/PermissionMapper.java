package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 导入 BaseMapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Mapper; // 导入 Mapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Select; // 导入 Select 类型或包供本文件使用

import java.util.List; // 导入 java.util.List 类型或包供本文件使用

@Mapper // 应用 Mapper 注解配置当前声明
public interface PermissionMapper extends BaseMapper<Permission> { // 声明 PermissionMapper 接口

    @Select("SELECT DISTINCT p.code FROM sys_permission p INNER JOIN sys_role_permission rp ON rp.permission_id = p.id INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id WHERE ur.user_id = #{userId}") // 按用户汇总权限编码
    List<String> selectCodesByUserId(Long userId); // 定义 selectCodesByUserId 方法的入口
} // 
