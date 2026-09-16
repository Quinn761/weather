package com.weatherhub.camera;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("camera_monitoring_record")
public class CameraMonitoringRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private Long snapshotId;
    private String status;
    private String weatherPredictions;
    private String visibilityPredictions;
    private Integer visibleBoatCount;
    private String boatPredictions;
    private String trackedBoats;
    private String outputImage;
    private String rawResult;
    private String errorMessage;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
}
