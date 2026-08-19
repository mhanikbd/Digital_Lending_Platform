package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Customer sign-in: mobile number and 6 digit PIN, from a bound device.
 *
 * <p>The PIN alone is weak. It is only accepted from a handset already proven
 * by OTP, which is what makes the pair defensible.
 *
 * <p>The patterns use explicit character classes rather than shorthand, so the
 * expression reads the same in the Java source as it does in the regex.
 */
@Schema(description = "Customer sign-in request")
public record CustomerLoginRequest(
        @NotBlank
        @Pattern(regexp = "^01[3-9][0-9]{8}$", message = "must be a Bangladeshi mobile number")
        @Schema(description = "Mobile number", example = "01712345678") String mobile,

        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "must be exactly 6 digits")
        @Schema(description = "6 digit PIN") String pin,

        @NotNull @Valid
        @Schema(description = "The handset this request comes from") DeviceDescriptor device) {
}
