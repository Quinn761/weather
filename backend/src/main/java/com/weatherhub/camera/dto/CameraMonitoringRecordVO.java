package com.weatherhub.camera.dto;

import com.weatherhub.camera.CameraMonitoringRecord;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

public record CameraMonitoringRecordVO(
        Long id, Long snapshotId, String status, Object weatherPredictions, Object visibilityPredictions,
        Integer visibleBoatCount, Object boatPredictions, Object trackedBoats, String outputImage,
        String errorMessage, LocalDateTime analyzedAt
) {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static CameraMonitoringRecordVO from(CameraMonitoringRecord record, boolean includeOutputImage) {
        return new CameraMonitoringRecordVO(record.getId(), record.getSnapshotId(), record.getStatus(),
                parse(record.getWeatherPredictions()), parse(record.getVisibilityPredictions()), record.getVisibleBoatCount(),
                parse(record.getBoatPredictions()), parse(record.getTrackedBoats()), includeOutputImage ? record.getOutputImage() : null,
                record.getErrorMessage(), record.getAnalyzedAt());
    }

    private static Object parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return JSON.readValue(value, Object.class); } catch (Exception ignored) { return value; }
    }
}
