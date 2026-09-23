package com.weatherhub.operations.dto;
import jakarta.validation.constraints.NotBlank;
public record AddWorkOrderProgressRequest(@NotBlank String content, Double longitude, Double latitude, String statusAfter) { }
