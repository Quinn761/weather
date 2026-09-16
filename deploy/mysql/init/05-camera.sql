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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS camera_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'image/jpeg',
    file_size BIGINT NOT NULL DEFAULT 0,
    captured_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_camera_snapshot_camera_time (camera_id, captured_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
