package com.weatherhub.gis.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveGisFeatureRequest(
        @NotBlank String type,
        @NotBlank String geojson,
        String properties
) {
}
