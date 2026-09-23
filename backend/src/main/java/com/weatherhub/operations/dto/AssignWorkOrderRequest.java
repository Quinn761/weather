package com.weatherhub.operations.dto;
import jakarta.validation.constraints.NotNull;
public record AssignWorkOrderRequest(@NotNull Long assigneeId) { }
