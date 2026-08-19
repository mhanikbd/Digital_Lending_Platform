package com.naztech.lending.organization.dto;

import com.naztech.lending.organization.domain.OrgUnitType;
import io.swagger.v3.oas.annotations.media.Schema;

/** One kind of unit the bank is made of. */
@Schema(description = "A kind of organisational unit")
public record OrgUnitTypeResponse(
        @Schema(example = "BRANCH") String code,
        String name,
        String description,
        @Schema(description = "The parent this type normally hangs from. Advisory", example = "REGION")
        String parentTypeCode,
        @Schema(description = "Depth in the conventional tree", example = "3") short hierarchyLevel,
        @Schema(description = "Whether this type serves customers over a counter")
        boolean customerFacing) {

    public static OrgUnitTypeResponse from(OrgUnitType type) {
        return new OrgUnitTypeResponse(
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.getParentTypeCode(),
                type.getHierarchyLevel(),
                type.isCustomerFacing());
    }
}
