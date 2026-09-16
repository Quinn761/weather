package com.weatherhub.camera;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("camera_snapshot")
public class CameraSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private String storagePath;
    private String contentType;
    private Long fileSize;
    private LocalDateTime capturedAt;
    private LocalDateTime createdAt;
}
