package com.weatherhub.gis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@ConditionalOnProperty(name = "weatherhub.gis.enabled", havingValue = "true", matchIfMissing = true)
public class GisSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate gisJdbcTemplate;

    public GisSchemaInitializer(@Qualifier("gisJdbcTemplate") JdbcTemplate gisJdbcTemplate) {
        this.gisJdbcTemplate = gisJdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        gisJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        gisJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS gis_feature (
                    id          BIGSERIAL PRIMARY KEY,
                    name        VARCHAR(128) NOT NULL,
                    type        VARCHAR(32)  NOT NULL,
                    geom        geometry(GeometryZ, 4326) NOT NULL,
                    properties  JSONB NOT NULL DEFAULT '{}'::jsonb,
                    created_at  TIMESTAMP(6) NOT NULL DEFAULT now(),
                    updated_at  TIMESTAMP(6) NOT NULL DEFAULT now()
                )
                """);
        gisJdbcTemplate.execute("DROP INDEX IF EXISTS idx_gis_feature_geom");
        gisJdbcTemplate.execute("""
                ALTER TABLE gis_feature
                ALTER COLUMN geom TYPE geometry(GeometryZ, 4326)
                USING ST_Force3D(geom)
                """);
        gisJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_gis_feature_geom ON gis_feature USING GIST (geom)");
        log.info("已确保 PostGIS gis_feature 表存在");
    }
}
