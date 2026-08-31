package com.weatherhub.gis.dto;

import com.weatherhub.gis.GisFeature;

import java.time.LocalDateTime;

public record GisFeatureVO(
        Long id,
        String name,
        String type,
        String geojson,
        String properties,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GisFeatureVO from(GisFeature feature) {
        return new GisFeatureVO(
                feature.getId(),
                feature.getName(),
                feature.getType(),
                feature.getGeojson(),
                feature.getProperties(),
                feature.getCreatedAt(),
                feature.getUpdatedAt()
        );
    }
}
