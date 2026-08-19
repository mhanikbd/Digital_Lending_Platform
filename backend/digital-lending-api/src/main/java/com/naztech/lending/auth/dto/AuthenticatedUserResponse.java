package com.naztech.lending.auth.dto;

import com.naztech.lending.auth.domain.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The identity behind the current token.
 *
 * <p>Carries no roles or permissions: authorisation is Milestone 6, and this
 * shape gains a scope list then rather than being reshaped.
 */
@Schema(description = "The signed-in identity")
public record AuthenticatedUserResponse(
        UUID id,
        @Schema(example = "EMP-10432") String username,
        String displayName,
        @Schema(example = "BANK_USER") String userType,
        @Schema(description = "True when the secret must be changed before anything else")
        boolean mustChangeSecret,
        Instant lastLoginAt) {

    public static AuthenticatedUserResponse from(UserAccount user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserType().name(),
                user.isMustChangeSecret(),
                user.getLastLoginAt());
    }
}
