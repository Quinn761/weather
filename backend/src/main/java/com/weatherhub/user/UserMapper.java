package com.weatherhub.user; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 导入 com.baomidou.mybatisplus.core.mapper.BaseMapper 类型或包供本文件使用
import org.apache.ibatis.annotations.Mapper; // 导入 org.apache.ibatis.annotations.Mapper 类型或包供本文件使用

@Mapper // 应用 Mapper 注解配置当前声明
public interface UserMapper extends BaseMapper<User> { // 声明 UserMapper 接口
} // 
