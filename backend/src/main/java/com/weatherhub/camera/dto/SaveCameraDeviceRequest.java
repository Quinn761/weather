package com.weatherhub.camera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCameraDeviceRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 80) String serialNumber,
        @Size(max = 80) String verificationCode,
        Double longitude,
        Double latitude,
        @Size(max = 16) String status
) {
}
