package com.weatherhub.common; // 声明当前 Java 文件所属的包路径

import lombok.AllArgsConstructor; // 导入 lombok.AllArgsConstructor 类型或包供本文件使用
import lombok.Data; // 导入 lombok.Data 类型或包供本文件使用
import lombok.NoArgsConstructor; // 导入 lombok.NoArgsConstructor 类型或包供本文件使用

@Data // 让 Lombok 生成 getter、setter、equals、hashCode 和 toString
@NoArgsConstructor // 让 Lombok 生成无参构造方法
@AllArgsConstructor // 让 Lombok 生成全参构造方法
public class ApiResponse<T> { // 声明 ApiResponse 类

    private int code; // 定义 code 字段保存对象状态或依赖
    private String message; // 定义 message 字段保存对象状态或依赖
    private T data; // 定义 data 字段保存对象状态或依赖

    public static <T> ApiResponse<T> ok(T data) { // 定义 ok 方法的入口
        return new ApiResponse<>(0, "success", data); // 返回当前方法的处理结果
    } // 

    public static <T> ApiResponse<T> ok() { // 定义 ok 方法的入口
        return ok(null); // 返回当前方法的处理结果
    } // 

    public static <T> ApiResponse<T> fail(int code, String message) { // 定义 fail 方法的入口
        return new ApiResponse<>(code, message, null); // 返回当前方法的处理结果
    } // 

    public static <T> ApiResponse<T> fail(String message) { // 定义 fail 方法的入口
        return fail(400, message); // 返回当前方法的处理结果
    } // 
} // 
