package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Asks for a one-time passcode to be sent to a customer. */
@Schema(description = "OTP issue request")
public record OtpRequest(
        @NotBlank
        @Pattern(regexp = "^01[3-9][0-9]{8}$", message = "must be a Bangladeshi mobile number")
        @Schema(example = "01712345678") String mobile) {
}
