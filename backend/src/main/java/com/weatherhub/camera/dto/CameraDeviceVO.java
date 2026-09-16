package com.weatherhub.camera.dto;

import com.weatherhub.camera.CameraDevice;

import java.time.LocalDateTime;

public record CameraDeviceVO(
        Long id,
        String name,
        String brand,
        String serialNumber,
        boolean verificationConfigured,
        Double longitude,
        Double latitude,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CameraDeviceVO from(CameraDevice device) {
        return new CameraDeviceVO(
                device.getId(), device.getName(), device.getBrand(), device.getSerialNumber(),
                device.getVerificationCode() != null && !device.getVerificationCode().isBlank(),
                device.getLongitude(), device.getLatitude(), device.getStatus(),
                device.getCreatedAt(), device.getUpdatedAt()
        );
    }
}
