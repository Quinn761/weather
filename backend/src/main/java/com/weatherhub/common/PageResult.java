package com.weatherhub.common; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 导入 com.baomidou.mybatisplus.extension.plugins.pagination.Page 类型或包供本文件使用

import java.util.List; // 导入 java.util.List 类型或包供本文件使用

public record PageResult<T>(List<T> records, long total, long current, long size) { // 声明 PageResult 记录类型及其字段

    public static <T> PageResult<T> of(Page<T> page) { // 定义 of 方法的入口
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()); // 返回当前方法的处理结果
    } // 
} // 
