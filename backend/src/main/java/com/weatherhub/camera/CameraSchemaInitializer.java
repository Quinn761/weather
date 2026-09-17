package com.weatherhub.camera;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class CameraSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS camera_device (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(80) NOT NULL,
                    brand VARCHAR(32) NOT NULL DEFAULT '海康',
                    serial_number VARCHAR(80) NOT NULL,
                    verification_code VARCHAR(512) NOT NULL,
                    longitude DECIMAL(10,7) NULL,
                    latitude DECIMAL(10,7) NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    UNIQUE KEY uk_camera_device_serial_number (serial_number)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS camera_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    camera_id BIGINT NOT NULL,
                    storage_path VARCHAR(512) NOT NULL,
                    content_type VARCHAR(64) NOT NULL DEFAULT 'image/jpeg',
                    file_size BIGINT NOT NULL DEFAULT 0,
                    captured_at DATETIME(6) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    KEY idx_camera_snapshot_camera_time (camera_id, captured_at DESC)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS camera_monitoring_record (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    camera_id BIGINT NOT NULL,
                    snapshot_id BIGINT NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
                    weather_predictions TEXT NULL,
                    visibility_predictions TEXT NULL,
                    visible_boat_count INT NULL,
                    boat_predictions MEDIUMTEXT NULL,
                    tracked_boats MEDIUMTEXT NULL,
                    output_image MEDIUMTEXT NULL,
                    raw_result MEDIUMTEXT NULL,
                    error_message TEXT NULL,
                    analyzed_at DATETIME(6) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    KEY idx_camera_monitoring_camera_time (camera_id, analyzed_at DESC),
                    KEY idx_camera_monitoring_snapshot (snapshot_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS camera_alert (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    camera_id BIGINT NOT NULL,
                    monitoring_record_id BIGINT NOT NULL,
                    type VARCHAR(64) NOT NULL,
                    level VARCHAR(16) NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    title VARCHAR(160) NOT NULL,
                    content TEXT NOT NULL,
                    confidence DECIMAL(5,4) NULL,
                    normal_count INT NOT NULL DEFAULT 0,
                    first_detected_at DATETIME(6) NOT NULL,
                    last_detected_at DATETIME(6) NOT NULL,
                    recovered_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    KEY idx_camera_alert_active (camera_id, type, status),
                    KEY idx_camera_alert_time (last_detected_at DESC)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
