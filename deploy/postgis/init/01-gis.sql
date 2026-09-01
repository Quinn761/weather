CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS gis_feature (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    geom        geometry(GeometryZ, 4326) NOT NULL,
    properties  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gis_feature_geom ON gis_feature USING GIST (geom);

ALTER TABLE gis_feature
    ALTER COLUMN geom TYPE geometry(GeometryZ, 4326)
    USING ST_Force3D(geom);
