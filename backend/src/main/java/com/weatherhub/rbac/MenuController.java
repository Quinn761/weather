package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.weatherhub.common.ApiResponse; // 导入 ApiResponse 类型或包供本文件使用
import com.weatherhub.rbac.dto.MenuVO; // 导入 MenuVO 类型或包供本文件使用
import com.weatherhub.rbac.dto.SaveMenuRequest; // 导入 SaveMenuRequest 类型或包供本文件使用
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
@RequestMapping("/api/menus") // 设置当前控制器或接口的请求路径
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class MenuController { // 声明 MenuController 类

    private final MenuService menuService; // 定义 menuService 字段保存对象状态或依赖

    @GetMapping // 声明处理 HTTP GET 请求的接口
    public ApiResponse<List<MenuVO>> tree() { // 定义 tree 方法的入口
        return ApiResponse.ok(menuService.tree()); // 返回完整菜单树，供管理页使用
    } // 

    @PostMapping // 声明处理 HTTP POST 请求的接口
    public ApiResponse<MenuVO> create(@Valid @RequestBody SaveMenuRequest request) { // 定义 create 方法的入口
        return ApiResponse.ok(menuService.create(request)); // 返回新建的菜单
    } // 

    @PutMapping("/{id}") // 声明处理 HTTP PUT 请求的接口
    public ApiResponse<MenuVO> update(@PathVariable Long id, @Valid @RequestBody SaveMenuRequest request) { // 定义 update 方法的入口
        return ApiResponse.ok(menuService.update(id, request)); // 返回更新后的菜单
    } // 

    @DeleteMapping("/{id}") // 声明处理 HTTP DELETE 请求的接口
    public ApiResponse<Void> delete(@PathVariable Long id) { // 定义 delete 方法的入口
        menuService.delete(id); // 删除菜单
        return ApiResponse.ok(); // 返回成功空结果
    } // 
} // 
