package com.weatherhub.dashboard; // 声明当前 Java 文件所属的包路径

import com.weatherhub.common.ApiResponse; // 导入 ApiResponse 类型或包供本文件使用
import com.weatherhub.dashboard.dto.OverviewVO; // 导入 OverviewVO 类型或包供本文件使用
import com.weatherhub.rbac.MenuService; // 导入 MenuService 类型或包供本文件使用
import com.weatherhub.rbac.RoleService; // 导入 RoleService 类型或包供本文件使用
import com.weatherhub.user.UserMapper; // 导入 UserMapper 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.beans.factory.ObjectProvider; // 导入 ObjectProvider 类型或包供本文件使用
import org.springframework.data.redis.connection.RedisConnection; // 导入 RedisConnection 类型或包供本文件使用
import org.springframework.data.redis.core.StringRedisTemplate; // 导入 StringRedisTemplate 类型或包供本文件使用
import org.springframework.web.bind.annotation.GetMapping; // 导入 GetMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestMapping; // 导入 RequestMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RestController; // 导入 RestController 类型或包供本文件使用

import java.util.LinkedHashMap; // 导入 LinkedHashMap 类型或包供本文件使用
import java.util.Map; // 导入 Map 类型或包供本文件使用

@RestController // 声明这是提供 HTTP 接口的控制器
@RequestMapping("/api") // 设置当前控制器或接口的请求路径
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class DashboardController { // 声明 DashboardController 类

    private final UserMapper userMapper; // 定义 userMapper 字段保存对象状态或依赖
    private final RoleService roleService; // 定义 roleService 字段保存对象状态或依赖
    private final MenuService menuService; // 定义 menuService 字段保存对象状态或依赖
    private final ObjectProvider<StringRedisTemplate> redis; // 定义 redis 字段，测试环境可能没有

    @GetMapping("/health") // 声明处理 HTTP GET 请求的接口
    public ApiResponse<Map<String, String>> health() { // 定义 health 方法的入口
        Map<String, String> data = new LinkedHashMap<>(); // 按固定顺序组装健康信息
        data.put("status", "UP"); // 应用本身可用
        data.put("name", "Weather Data Hub"); // 服务名称
        data.put("redis", redisStatus()); // Redis 是否能 PING 通
        return ApiResponse.ok(data); // 返回健康检查结果
    } //

    private String redisStatus() { // 探测 Redis 是否可用
        StringRedisTemplate template = redis.getIfAvailable(); // 没有 Redis 客户端时返回空
        if (template == null || template.getConnectionFactory() == null) { // 测试环境或未装配 Redis
            return "DOWN"; // 标记为不可用
        } //
        try (RedisConnection connection = template.getConnectionFactory().getConnection()) { // 打开一条短连接
            String pong = connection.ping(); // 发送 PING
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN"; // 只有 PONG 视为正常
        } catch (Exception ex) { // Redis 连不上时不让健康检查抛错
            return "DOWN"; // 标记为不可用
        } //
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
