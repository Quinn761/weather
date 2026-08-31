package com.weatherhub.common; // 声明当前 Java 文件所属的包路径

import lombok.Getter; // 导入 lombok.Getter 类型或包供本文件使用

@Getter // 应用 Getter 注解配置当前声明
public class BusinessException extends RuntimeException { // 声明 BusinessException 类

    private final int code; // 定义 code 字段保存对象状态或依赖

    public BusinessException(String message) { // 定义构造方法并接收依赖或参数
        this(400, message); // 执行 this 语句完成当前步骤
    } // 

    public BusinessException(int code, String message) { // 定义构造方法并接收依赖或参数
        super(message); // 执行 super 语句完成当前步骤
        this.code = code; // 计算并保存 this.code 的值
    } // 
} // 
