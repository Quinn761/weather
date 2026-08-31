package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.weatherhub.common.ApiResponse; // 导入 ApiResponse 类型或包供本文件使用
import com.weatherhub.rbac.dto.RoleVO; // 导入 RoleVO 类型或包供本文件使用
import com.weatherhub.rbac.dto.SaveRoleRequest; // 导入 SaveRoleRequest 类型或包供本文件使用
import jakarta.validation.Valid; // 导入 Valid 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.web.bind.annotation.DeleteMapping; // 导入 DeleteMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.GetMapping; // 导入 GetMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.PathVariable; // 导入 PathVariable 类型或包供本文件使用
import org.springframework.web.bind.annotation.PostMapping; // 导入 PostMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.PutMapping; // 导入 PutMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestBody; // 导入 RequestBody 类型或包供本文件使用
import org.springframework.web.bind.annotation.RequestMapping; // 导入 RequestMapping 类型或包供本文件使用
import org.springframework.web.bind.annotation.RestController; // 导入 RestController 类型或包供本文件使用

import java.util.List; // 导入 List 类型或包供本文件使用

@RestController // 声明这是提供 HTTP 接口的控制器
@RequestMapping("/api/roles") // 设置当前控制器或接口的请求路径
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class RoleController { // 声明 RoleController 类

    private final RoleService roleService; // 定义 roleService 字段保存对象状态或依赖

    @GetMapping // 声明处理 HTTP GET 请求的接口
    public ApiResponse<List<RoleVO>> list() { // 定义 list 方法的入口
        return ApiResponse.ok(roleService.list()); // 返回角色列表
    } // 

    @GetMapping("/{id}") // 声明处理 HTTP GET 请求的接口
    public ApiResponse<RoleVO> get(@PathVariable Long id) { // 定义 get 方法的入口
        return ApiResponse.ok(roleService.get(id)); // 返回单个角色
    } // 

    @PostMapping // 声明处理 HTTP POST 请求的接口
    public ApiResponse<RoleVO> create(@Valid @RequestBody SaveRoleRequest request) { // 定义 create 方法的入口
        return ApiResponse.ok(roleService.create(request)); // 返回新建的角色
    } // 

    @PutMapping("/{id}") // 声明处理 HTTP PUT 请求的接口
    public ApiResponse<RoleVO> update(@PathVariable Long id, @Valid @RequestBody SaveRoleRequest request) { // 定义 update 方法的入口
        return ApiResponse.ok(roleService.update(id, request)); // 返回更新后的角色
    } // 

    @DeleteMapping("/{id}") // 声明处理 HTTP DELETE 请求的接口
    public ApiResponse<Void> delete(@PathVariable Long id) { // 定义 delete 方法的入口
        roleService.delete(id); // 删除角色
        return ApiResponse.ok(); // 返回成功空结果
    } // 
} // 
