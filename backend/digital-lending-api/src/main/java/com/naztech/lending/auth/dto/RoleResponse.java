package com.naztech.lending.auth.dto;

import com.naztech.lending.auth.domain.Permission;
import com.naztech.lending.auth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

/** A role and what it currently permits. */
@Schema(description = "A configurable role")
public record RoleResponse(
        @Schema(example = "BM") String code,
        String name,
        String description,
        @Schema(description = "How far a holder can see once the organisation tree exists",
                example = "BRANCH") String scopeLevel,
        @Schema(description = "Seeded with the product; re-permissionable but not deletable")
        boolean systemRole,
        String status,
        @Schema(description = "Permission codes this role grants") List<String> permissions) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getScopeLevel().name(),
                role.isSystemRole(),
                role.getStatus(),
                role.getPermissions().stream()
                        .map(Permission::getCode)
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }
}
