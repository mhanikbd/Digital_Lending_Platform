package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Identifies the handset a request comes from. Required for customer and field
 * officer journeys, where the device is a factor; optional for staff on a
 * browser.
 */
@Schema(description = "The device a sign-in request originates from")
public record DeviceDescriptor(
        @NotBlank @Size(max = 128)
        @Schema(description = "Stable client-generated id for this install", example = "d41d8cd9-8f00")
        String deviceId,
        @Size(max = 20) @Schema(example = "ANDROID") String platform,
        @Size(max = 80) @Schema(example = "Pixel 8") String model,
        @Size(max = 40) @Schema(example = "14") String osVersion,
        @Size(max = 40) @Schema(example = "1.0.0") String appVersion) {
}
