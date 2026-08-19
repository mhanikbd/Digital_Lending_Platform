package com.naztech.lending.auth.dto;

import com.naztech.lending.auth.domain.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The identity behind the current token.
 *
 * <p>Carries the roles held and the permission codes they resolve to, so a
 * client can hide a control it would only be refused for pressing. The list is
 * advisory: the server refuses the call regardless of what the client rendered.
 */
@Schema(description = "The signed-in identity")
public record AuthenticatedUserResponse(
        UUID id,
        @Schema(example = "EMP-10432") String username,
        String displayName,
        @Schema(example = "BANK_USER") String userType,
        @Schema(description = "True when the secret must be changed before anything else")
        boolean mustChangeSecret,
        Instant lastLoginAt,
        @Schema(description = "Role codes held, for example BM or CA") List<String> roles,
        @Schema(description = "Permission codes the roles resolve to") List<String> permissions) {

    public static AuthenticatedUserResponse from(UserAccount user,
                                                 List<String> roles,
                                                 List<String> permissions) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserType().name(),
                user.isMustChangeSecret(),
                user.getLastLoginAt(),
                List.copyOf(roles),
                List.copyOf(permissions));
    }
}
