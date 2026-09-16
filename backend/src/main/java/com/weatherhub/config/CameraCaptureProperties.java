package com.weatherhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weatherhub.camera.capture")
public record CameraCaptureProperties(
        boolean enabled,
        String streamUrl,
        String storagePath,
        String ffmpegCommand,
        int timeoutSeconds
) {
    public CameraCaptureProperties {
        storagePath = (storagePath == null || storagePath.isBlank()) ? "data/camera-snapshots" : storagePath;
        ffmpegCommand = (ffmpegCommand == null || ffmpegCommand.isBlank()) ? "ffmpeg" : ffmpegCommand;
        timeoutSeconds = timeoutSeconds <= 0 ? 45 : timeoutSeconds;
    }
}
