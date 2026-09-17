package com.weatherhub.camera;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("camera_alert")
public class CameraAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private Long monitoringRecordId;
    private String type;
    private String level;
    private String status;
    private String title;
    private String content;
    private Double confidence;
    private Integer normalCount;
    private LocalDateTime firstDetectedAt;
    private LocalDateTime lastDetectedAt;
    private LocalDateTime recoveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
