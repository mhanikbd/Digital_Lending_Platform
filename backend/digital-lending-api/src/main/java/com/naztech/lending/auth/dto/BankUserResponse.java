package com.naztech.lending.auth.dto;

import com.naztech.lending.auth.domain.Role;
import com.naztech.lending.auth.domain.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A bank user as an administrator sees them.
 *
 * <p>Carries no credential, no hash and no session. There is deliberately no
 * shape in this application that can return any of those.
 */
@Schema(description = "A bank user")
public record BankUserResponse(
        UUID id,
        @Schema(example = "EMP-10001") String username,
        String displayName,
        String status,
        boolean mfaEnabled,
        Instant lastLoginAt,
        @Schema(description = "Role codes held") List<String> roles) {

    public static BankUserResponse from(UserAccount user) {
        return new BankUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.isMfaEnabled(),
                user.getLastLoginAt(),
                user.getRoles().stream()
                        .map(Role::getCode)
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }
}
