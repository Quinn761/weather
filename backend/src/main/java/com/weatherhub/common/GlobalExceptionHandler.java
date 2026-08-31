package com.weatherhub.common; // 声明当前 Java 文件所属的包路径

import lombok.extern.slf4j.Slf4j; // 导入 lombok.extern.slf4j.Slf4j 类型或包供本文件使用
import org.springframework.http.HttpStatus; // 导入 org.springframework.http.HttpStatus 类型或包供本文件使用
import org.springframework.validation.BindException; // 导入 org.springframework.validation.BindException 类型或包供本文件使用
import org.springframework.web.bind.MethodArgumentNotValidException; // 导入 org.springframework.web.bind.MethodArgumentNotValidException 类型或包供本文件使用
import org.springframework.web.bind.annotation.ExceptionHandler; // 导入 org.springframework.web.bind.annotation.ExceptionHandler 类型或包供本文件使用
import org.springframework.web.bind.annotation.ResponseStatus; // 导入 org.springframework.web.bind.annotation.ResponseStatus 类型或包供本文件使用
import org.springframework.web.bind.annotation.RestControllerAdvice; // 导入 org.springframework.web.bind.annotation.RestControllerAdvice 类型或包供本文件使用
import org.springframework.web.multipart.MaxUploadSizeExceededException; // 导入 MaxUploadSizeExceededException 类型或包供本文件使用
import org.springframework.web.servlet.resource.NoResourceFoundException; // 导入 NoResourceFoundException 类型或包供本文件使用

@Slf4j // 让 Lombok 注入日志对象
@RestControllerAdvice // 声明这是统一处理控制器异常的组件
public class GlobalExceptionHandler { // 声明 GlobalExceptionHandler 类

    @ExceptionHandler(BusinessException.class) // 声明该方法处理指定异常
    public ApiResponse<Void> handleBusiness(BusinessException ex) { // 定义 handleBusiness 方法的入口
        return ApiResponse.fail(ex.getCode(), ex.getMessage()); // 返回当前方法的处理结果
    } // 

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class}) // 声明该方法处理指定异常
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 指定异常处理返回的 HTTP 状态码
    public ApiResponse<Void> handleValidation(Exception ex) { // 定义 handleValidation 方法的入口
        String message = "参数校验失败"; // 计算并保存 message 的值
        if (ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) { // 判断条件是否成立以决定是否进入分支
            message = manv.getBindingResult().getFieldError().getDefaultMessage(); // 计算并保存 message 的值
        } else if (ex instanceof BindException bind && bind.getBindingResult().getFieldError() != null) { // 结束前一分支并继续判断新条件
            message = bind.getBindingResult().getFieldError().getDefaultMessage(); // 计算并保存 message 的值
        } // 
        return ApiResponse.fail(400, message); // 返回当前方法的处理结果
    } // 

    @ExceptionHandler(MaxUploadSizeExceededException.class) // 声明该方法处理指定异常
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE) // 指定异常处理返回的 HTTP 状态码
    public ApiResponse<Void> handleMaxUpload(MaxUploadSizeExceededException ex) { // 定义 handleMaxUpload 方法的入口
        return ApiResponse.fail(413, "上传文件超过大小限制（最大 50MB）"); // 返回当前方法的处理结果
    } // 

    @ExceptionHandler(NoResourceFoundException.class) // 声明该方法处理指定异常
    @ResponseStatus(HttpStatus.NOT_FOUND) // 指定异常处理返回的 HTTP 状态码
    public ApiResponse<Void> handleNotFound(NoResourceFoundException ex) { // 定义 handleNotFound 方法的入口
        return ApiResponse.fail(404, "接口不存在"); // 已删除的数据/任务等路径返回 404
    } // 

    @ExceptionHandler(Exception.class) // 声明该方法处理指定异常
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // 指定异常处理返回的 HTTP 状态码
    public ApiResponse<Void> handleOther(Exception ex) { // 定义 handleOther 方法的入口
        log.error("Unhandled error", ex); // 执行 error 语句完成当前步骤
        return ApiResponse.fail(500, "服务器内部错误"); // 返回当前方法的处理结果
    } // 
} // 
