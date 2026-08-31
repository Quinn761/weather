package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 导入 BaseMapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Mapper; // 导入 Mapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Select; // 导入 Select 类型或包供本文件使用

import java.util.List; // 导入 java.util.List 类型或包供本文件使用

@Mapper // 应用 Mapper 注解配置当前声明
public interface RoleMapper extends BaseMapper<Role> { // 声明 RoleMapper 接口

    @Select("SELECT r.id, r.code, r.name, r.created_at, r.updated_at FROM sys_role r INNER JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId}") // 按用户查询已分配角色
    List<Role> selectByUserId(Long userId); // 定义 selectByUserId 方法的入口
} // 
