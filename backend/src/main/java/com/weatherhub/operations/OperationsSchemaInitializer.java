package com.weatherhub.operations;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class OperationsSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ops_event (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, source_type VARCHAR(32) NOT NULL, source_key VARCHAR(160) NULL, level VARCHAR(16) NOT NULL DEFAULT 'YELLOW', status VARCHAR(16) NOT NULL DEFAULT 'OPEN', title VARCHAR(160) NOT NULL, content TEXT NULL, region_name VARCHAR(80) NULL, longitude DECIMAL(10,7) NULL, latitude DECIMAL(10,7) NULL, occurred_at DATETIME(6) NOT NULL, created_by BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), UNIQUE KEY uk_ops_event_source (source_type, source_key), KEY idx_ops_event_status_time (status, occurred_at DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ops_work_order (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, event_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL, description TEXT NULL, status VARCHAR(32) NOT NULL DEFAULT 'TODO', priority VARCHAR(16) NOT NULL DEFAULT 'YELLOW', assignee_id BIGINT NULL, created_by BIGINT NOT NULL, due_at DATETIME(6) NULL, completed_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), KEY idx_ops_work_order_status_due (status, due_at), KEY idx_ops_work_order_event (event_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ops_work_order_collaborator (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, work_order_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_ops_order_collaborator (work_order_id,user_id), KEY idx_ops_order_collaborator_user (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ops_work_order_progress (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, work_order_id BIGINT NOT NULL, content TEXT NOT NULL, image_urls TEXT NULL, longitude DECIMAL(10,7) NULL, latitude DECIMAL(10,7) NULL, status_after VARCHAR(32) NULL, created_by BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_ops_order_progress_time (work_order_id,created_at DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        // Existing deployments may have been created with VARCHAR(16), which cannot store PENDING_ACCEPTANCE.
        jdbc.execute("ALTER TABLE ops_work_order MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'TODO'");
        jdbc.execute("ALTER TABLE ops_work_order_progress MODIFY COLUMN status_after VARCHAR(32) NULL");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ops_notification (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, type VARCHAR(32) NOT NULL, title VARCHAR(160) NOT NULL, content VARCHAR(500) NULL, target_type VARCHAR(32) NULL, target_id BIGINT NULL, read_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_ops_notification_user_read (user_id, read_at, created_at DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }
}
