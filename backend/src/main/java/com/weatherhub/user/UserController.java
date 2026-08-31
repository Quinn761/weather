package com.weatherhub.user; // 声明当前 Java 文件所属的包路径

import com.weatherhub.common.ApiResponse; // 导入 com.weatherhub.common.ApiResponse 类型或包供本文件使用
import com.weatherhub.common.PageResult; // 导入 com.weatherhub.common.PageResult 类型或包供本文件使用
import com.weatherhub.user.dto.CreateUserRequest; // 导入 com.weatherhub.user.dto.CreateUserRequest 类型或包供本文件使用
import com.weatherhub.user.dto.UpdateUserRequest; // 导入 com.weatherhub.user.dto.UpdateUserRequest 类型或包供本文件使用
import com.weatherhub.user.dto.UserVO; // 导入 com.weatherhub.user.dto.UserVO 类型或包供本文件使用
import jakarta.validation.Valid; // 导入 jakarta.validation.Valid 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 lombok.RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.web.bind.annotation.DeleteMapping; // 导入 org.springframework.web.bind.annotation.DeleteMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.GetMapping; // 导入 org.springframework.web.bind.annotation.GetMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.PathVariable; // 导入 org.springframework.web.bind.annotation.PathVariable 类型或包供本文件使用
import org.springframework.web.bind.annotation.PostMapping; // 导入 org.springframework.web.bind.annotation.PostMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.PutMapping; // 导入 org.springframework.web.bind.annotation.PutMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestBody; // 导入 org.springframework.web.bind.annotation.RequestBody 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestMapping; // 导入 org.springframework.web.bind.annotation.RequestMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestParam; // 导入 org.springframework.web.bind.annotation.RequestParam 类型或包供本文件使用
import org.springframework.web.bind.annotation.RestController; // 导入 org.springframework.web.bind.annotation.RestController 类型或包供本文件使用

@RestController // 声明这是提供 HTTP 接口的控制器
@RequestMapping("/api/users") // 设置当前控制器或接口的请求路径
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class UserController { // 声明 UserController 类

    private final UserService userService; // 定义 userService 字段保存对象状态或依赖

    @GetMapping // 声明处理 HTTP GET 请求的接口
    public ApiResponse<PageResult<UserVO>> page( // 执行当前 Java 代码行的声明或逻辑
            @RequestParam(defaultValue = "1") long current, // 应用 RequestParam 注解配置当前声明
            @RequestParam(defaultValue = "10") long size, // 应用 RequestParam 注解配置当前声明
            @RequestParam(required = false) String keyword // 应用 RequestParam 注解配置当前声明
    ) { // 开始当前声明或控制结构的代码块
        return ApiResponse.ok(PageResult.of(userService.page(current, size, keyword))); // 返回当前方法的处理结果
    } // 

    @GetMapping("/{id}") // 声明处理 HTTP GET 请求的接口
    public ApiResponse<UserVO> get(@PathVariable Long id) { // 定义 get 方法的入口
        return ApiResponse.ok(userService.get(id)); // 返回当前方法的处理结果
    } // 

    @PostMapping // 声明处理 HTTP POST 请求的接口
    public ApiResponse<UserVO> create(@Valid @RequestBody CreateUserRequest request) { // 定义 create 方法的入口
        return ApiResponse.ok(userService.create(request)); // 返回当前方法的处理结果
    } // 

    @PutMapping("/{id}") // 应用 PutMapping 注解配置当前声明
    public ApiResponse<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) { // 定义 update 方法的入口
        return ApiResponse.ok(userService.update(id, request)); // 返回当前方法的处理结果
    } // 

    @DeleteMapping("/{id}") // 声明处理 HTTP DELETE 请求的接口
    public ApiResponse<Void> delete(@PathVariable Long id) { // 定义 delete 方法的入口
        userService.delete(id); // 执行 delete 语句完成当前步骤
        return ApiResponse.ok(); // 返回当前方法的处理结果
    } // 
} // 
