package com.weatherhub.dashboard; // 声明当前 Java 文件所属的包路径

import com.weatherhub.common.ApiResponse; // 导入 ApiResponse 类型或包供本文件使用
import com.weatherhub.dashboard.dto.OverviewVO; // 导入 OverviewVO 类型或包供本文件使用
import com.weatherhub.rbac.MenuService; // 导入 MenuService 类型或包供本文件使用
import com.weatherhub.rbac.RoleService; // 导入 RoleService 类型或包供本文件使用
import com.weatherhub.user.UserMapper; // 导入 UserMapper 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.web.bind.annotation.GetMapping; // 导入 GetMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestMapping; // 导入 RequestMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RestController; // 导入 RestController 类型或包供本文件使用

import java.util.Map; // 导入 Map 类型或包供本文件使用

@RestController // 声明这是提供 HTTP 接口的控制器
@RequestMapping("/api") // 设置当前控制器或接口的请求路径
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class DashboardController { // 声明 DashboardController 类

    private final UserMapper userMapper; // 定义 userMapper 字段保存对象状态或依赖
    private final RoleService roleService; // 定义 roleService 字段保存对象状态或依赖
    private final MenuService menuService; // 定义 menuService 字段保存对象状态或依赖

    @GetMapping("/health") // 声明处理 HTTP GET 请求的接口
    public ApiResponse<Map<String, String>> health() { // 定义 health 方法的入口
        return ApiResponse.ok(Map.of( // 返回当前方法的处理结果
                "status", "UP", // 声明枚举值或多行参数的一项
                "name", "Weather Data Hub" // 执行当前 Java 代码行的声明或逻辑
        )); // 执行 )); 语句完成当前步骤
    } // 

    @GetMapping("/dashboard/overview") // 声明处理 HTTP GET 请求的接口
    public ApiResponse<OverviewVO> overview() { // 定义 overview 方法的入口
        return ApiResponse.ok(new OverviewVO( // 返回用户、角色、菜单数量
                userMapper.selectCount(null), // 统计系统用户数
                roleService.countAll(), // 统计角色数
                menuService.countAll() // 统计菜单数
        )); // 执行 )); 语句完成当前步骤
    } // 
} // 
