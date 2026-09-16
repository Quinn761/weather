package com.weatherhub.camera.dto;

import com.weatherhub.camera.CameraSnapshot;

import java.time.LocalDateTime;

/**
 * A snapshot list item includes a data URL so the browser does not make one image request per record.
 */
public record CameraSnapshotVO(Long id, Long cameraId, String imageDataUrl, Long fileSize, LocalDateTime capturedAt) {
    public static CameraSnapshotVO from(CameraSnapshot snapshot, String imageDataUrl) {
        return new CameraSnapshotVO(
                snapshot.getId(), snapshot.getCameraId(),
                imageDataUrl,
                snapshot.getFileSize(), snapshot.getCapturedAt()
        );
    }
}
