package com.weatherhub.operations.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import java.time.LocalDateTime;
public record SaveWorkOrderRequest(@NotNull Long eventId, @NotBlank String title, String description, String priority, @NotNull Long assigneeId, LocalDateTime dueAt) { }
