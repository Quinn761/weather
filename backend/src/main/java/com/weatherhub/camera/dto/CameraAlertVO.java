package com.weatherhub.camera.dto;

import com.weatherhub.camera.CameraAlert;

import java.time.LocalDateTime;

public record CameraAlertVO(Long id, Long cameraId, Long monitoringRecordId, String type, String level,
                            String status, String title, String content, Double confidence,
                            LocalDateTime firstDetectedAt, LocalDateTime lastDetectedAt, LocalDateTime recoveredAt) {
    public static CameraAlertVO from(CameraAlert alert) {
        return new CameraAlertVO(alert.getId(), alert.getCameraId(), alert.getMonitoringRecordId(), alert.getType(),
                alert.getLevel(), alert.getStatus(), alert.getTitle(), alert.getContent(), alert.getConfidence(),
                alert.getFirstDetectedAt(), alert.getLastDetectedAt(), alert.getRecoveredAt());
    }
}
