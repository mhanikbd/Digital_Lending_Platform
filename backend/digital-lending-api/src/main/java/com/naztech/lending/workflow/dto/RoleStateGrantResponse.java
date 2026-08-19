package com.naztech.lending.workflow.dto;

import com.naztech.lending.workflow.domain.RoleStateMap;
import io.swagger.v3.oas.annotations.media.Schema;

/** One grant: this role may take this action in this state. */
@Schema(description = "A role/state permission")
public record RoleStateGrantResponse(
        @Schema(example = "BM") String roleCode,
        @Schema(example = "SO_RECOMMENDED") String stateCode,
        @Schema(example = "RECOMMEND") String action,
        @Schema(example = "ACTIVE") String status) {

    public static RoleStateGrantResponse from(RoleStateMap grant) {
        return new RoleStateGrantResponse(grant.getRoleCode(), grant.getStateCode(),
                grant.getAction().name(), grant.getStatus());
    }
}
