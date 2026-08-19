package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Staff sign-in: employee id and password. */
@Schema(description = "Bank user sign-in request")
public record BankLoginRequest(
        @NotBlank @Size(max = 64)
        @Schema(description = "Employee id", example = "EMP-10432") String username,

        @NotBlank @Size(max = 200)
        @Schema(description = "Password. Never logged and never echoed back") String password) {
}
