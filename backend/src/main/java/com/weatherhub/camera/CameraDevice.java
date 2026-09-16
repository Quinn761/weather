package com.weatherhub.camera;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("camera_device")
public class CameraDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String brand;
    private String serialNumber;
    private String verificationCode;
    private Double longitude;
    private Double latitude;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
