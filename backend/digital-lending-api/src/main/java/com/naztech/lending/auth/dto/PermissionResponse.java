package com.naztech.lending.auth.dto;

import com.naztech.lending.auth.domain.Permission;
import io.swagger.v3.oas.annotations.media.Schema;

/** One entry from the permission catalogue. */
@Schema(description = "A thing that can be permitted")
public record PermissionResponse(
        @Schema(example = "admin.role.view") String code,
        String name,
        String description,
        @Schema(description = "Grouping for administration screens", example = "ADMIN") String module) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getModule());
    }
}
