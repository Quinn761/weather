package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 导入 BaseMapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Delete; // 导入 Delete 类型或包供本文件使用
import org.apache.ibatis.annotations.Insert; // 导入 Insert 类型或包供本文件使用
import org.apache.ibatis.annotations.Mapper; // 导入 Mapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Param; // 导入 Param 类型或包供本文件使用

@Mapper // 应用 Mapper 注解配置当前声明
public interface UserRoleMapper extends BaseMapper<UserRole> { // 声明 UserRoleMapper 接口

    @Insert("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})") // 写入用户角色关系并忽略重复
    int insertIgnore(@Param("userId") Long userId, @Param("roleId") Long roleId); // 定义 insertIgnore 方法的入口

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}") // 按用户删除全部角色关系
    int deleteByUserId(@Param("userId") Long userId); // 定义 deleteByUserId 方法的入口
} // 

