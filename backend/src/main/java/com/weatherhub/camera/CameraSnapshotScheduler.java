package com.weatherhub.camera;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weatherhub.config.CameraCaptureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CameraSnapshotScheduler {
    private final CameraCaptureProperties properties;
    private final CameraDeviceMapper cameraDeviceMapper;
    private final CameraSnapshotService snapshotService;
    private final CameraMonitoringService monitoringService;

    // Run at minute 0 and 30 of every hour; AI analysis is triggered immediately after each capture.
    @Scheduled(cron = "0 0/30 * * * *")
    public void captureOnlineCameras() {
        if (!properties.enabled()) return;
        var cameras = cameraDeviceMapper.selectList(new LambdaQueryWrapper<CameraDevice>()
                .eq(CameraDevice::getStatus, "ONLINE"));
        for (CameraDevice camera : cameras) {
            try {
                CameraSnapshot snapshot = snapshotService.capture(camera);
                monitoringService.analyzeAndSave(snapshot);
                log.info("Camera snapshot and AI monitoring saved: cameraId={}, snapshotId={}", camera.getId(), snapshot.getId());
            } catch (Exception ex) {
                log.warn("Camera snapshot failed: cameraId={}, reason={}", camera.getId(), ex.getMessage());
            }
        }
    }
}
