package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Exchanges a refresh token for a new access token. */
@Schema(description = "Refresh request")
public record RefreshTokenRequest(
        @NotBlank @Size(max = 200) @Schema(description = "The opaque refresh token") String refreshToken) {
}
