package com.naztech.lending.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The outcome of a sign-in attempt.
 *
 * <p>Two shapes, one endpoint: either the caller is authenticated and holds
 * tokens, or a second factor is owed and holds a challenge id. A client
 * branches on {@code status} rather than on which fields happen to be null.
 */
@Schema(description = "Sign-in outcome")
public record LoginResponse(
        @Schema(description = "AUTHENTICATED or MFA_REQUIRED", example = "AUTHENTICATED") String status,
        @Schema(description = "Present when status is AUTHENTICATED") TokenPair tokens,
        @Schema(description = "Present when status is AUTHENTICATED") AuthenticatedUserResponse user,
        @Schema(description = "Present when status is MFA_REQUIRED") String mfaChallengeId,
        @Schema(description = "Seconds the challenge remains valid") Long mfaExpiresInSeconds) {

    public static LoginResponse authenticated(TokenPair tokens, AuthenticatedUserResponse user) {
        return new LoginResponse("AUTHENTICATED", tokens, user, null, null);
    }

    public static LoginResponse mfaRequired(String challengeId, long expiresInSeconds) {
        return new LoginResponse("MFA_REQUIRED", null, null, challengeId, expiresInSeconds);
    }
}
