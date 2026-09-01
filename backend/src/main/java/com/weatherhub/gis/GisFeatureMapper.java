package com.weatherhub.gis;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GisFeatureMapper {

    @Select("""
            SELECT id,
                   name,
                   type,
                   json_build_object(
                       'type', 'Feature',
                       'geometry', ST_AsGeoJSON(geom, 7)::json,
                       'properties', '{}'::json
                   )::text AS geojson,
                   properties::text AS properties,
                   created_at,
                   updated_at
            FROM gis_feature
            ORDER BY created_at DESC, id DESC
            """)
    List<GisFeature> selectAllFeatures();

    @Insert("""
            INSERT INTO gis_feature (name, type, geom, properties, created_at, updated_at)
            VALUES (
                #{name},
                #{type},
                ST_SetSRID(ST_Force3D(ST_GeomFromGeoJSON(#{geometryJson})), 4326),
                CAST(#{properties} AS jsonb),
                now(),
                now()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertFeature(GisFeature feature);

    @Delete("DELETE FROM gis_feature WHERE id = #{id}")
    int deleteById(Long id);
}
