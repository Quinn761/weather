package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import com.weatherhub.auth.dto.LoginRequest; // 导入 LoginRequest 类型或包供本文件使用
import com.weatherhub.auth.dto.LoginVO; // 导入 LoginVO 类型或包供本文件使用
import com.weatherhub.common.ApiResponse; // 导入 ApiResponse 类型或包供本文件使用
import com.weatherhub.user.dto.UserVO; // 导入 UserVO 类型或包供本文件使用
import jakarta.validation.Valid; // 导入 Valid 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.security.core.Authentication; // 导入 Authentication 类型或包供本文件使用
import org.springframework.web.bind.annotation.GetMapping; // 导入 GetMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.PostMapping; // 导入 PostMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestBody; // 导入 RequestBody 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestMapping; // 导入 RequestMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RestController; // 导入 RestController 类型或包供本文件使用

@RestController // 声明这是提供 HTTP 接口的控制器
@RequestMapping("/api/auth") // 设置当前控制器或接口的请求路径
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class AuthController { // 声明 AuthController 类

    private final AuthService authService; // 定义 authService 字段保存对象状态或依赖

    @PostMapping("/login") // 声明处理 HTTP POST 请求的接口
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) { // 定义 login 方法的入口
        return ApiResponse.ok(authService.login(request)); // 返回当前方法的处理结果
    } // 

    @GetMapping("/me") // 声明处理 HTTP GET 请求的接口
    public ApiResponse<UserVO> me(Authentication authentication) { // 定义 me 方法的入口
        Long userId = Long.valueOf(authentication.getName()); // 计算并保存 userId 的值
        return ApiResponse.ok(authService.currentUser(userId)); // 返回当前方法的处理结果
    } // 
} // 
