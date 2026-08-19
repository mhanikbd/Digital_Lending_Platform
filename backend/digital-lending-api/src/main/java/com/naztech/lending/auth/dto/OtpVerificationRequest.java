package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Presents a one-time passcode, binding the handset it came from. */
@Schema(description = "OTP verification request")
public record OtpVerificationRequest(
        @NotBlank @Size(max = 64)
        @Schema(description = "Challenge id returned when the code was requested") String challengeId,

        @NotBlank
        @Pattern(regexp = "^01[3-9][0-9]{8}$", message = "must be a Bangladeshi mobile number")
        @Schema(example = "01712345678") String mobile,

        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "must be exactly 6 digits")
        @Schema(description = "The code that was sent") String code,

        @NotNull @Valid
        @Schema(description = "The handset being bound") DeviceDescriptor device) {
}
