package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a successful authentication yields.
 *
 * <p>The access token is a signed JWT and is not revocable, which is why it is
 * short lived. The refresh token is opaque and server-side, which is what makes
 * a session revocable.
 */
@Schema(description = "Issued tokens")
public record TokenPair(
        @Schema(description = "Signed JWT for the Authorization header") String accessToken,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresInSeconds,
        @Schema(description = "Opaque token used to obtain a new access token") String refreshToken,
        @Schema(description = "Refresh token lifetime in seconds", example = "2592000")
        long refreshExpiresInSeconds) {
}
