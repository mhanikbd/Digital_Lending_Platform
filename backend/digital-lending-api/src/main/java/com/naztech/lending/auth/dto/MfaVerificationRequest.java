package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Second factor for a staff sign-in that returned MFA_REQUIRED. */
@Schema(description = "MFA challenge response")
public record MfaVerificationRequest(
        @NotBlank @Size(max = 64)
        @Schema(description = "Challenge id returned by the first step") String challengeId,

        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "must be exactly 6 digits")
        @Schema(description = "The code that was sent") String code) {
}
