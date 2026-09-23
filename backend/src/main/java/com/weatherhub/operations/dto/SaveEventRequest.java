package com.weatherhub.operations.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Pattern; import java.time.LocalDateTime;
public record SaveEventRequest(@NotBlank @Pattern(regexp="OFFICIAL_ALERT|TROPICAL_CYCLONE|CAMERA_ALERT|MANUAL") String sourceType, String sourceKey, @NotBlank String title, String content, String regionName, String level, Double longitude, Double latitude, LocalDateTime occurredAt) { }
