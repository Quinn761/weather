package com.weatherhub.operations.dto;
import jakarta.validation.constraints.NotBlank;
public record AcceptWorkOrderRequest(boolean approved, @NotBlank String comment) { }
