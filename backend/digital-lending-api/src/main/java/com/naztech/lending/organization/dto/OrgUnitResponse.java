package com.naztech.lending.organization.dto;

import com.naztech.lending.organization.domain.OrgUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A unit and everything beneath it.
 *
 * <p>Nested rather than flat with a parent id: the client that asks for an
 * organisation almost always wants to draw it, and a flat list makes every
 * caller reimplement the same assembly.
 */
@Schema(description = "An organisational unit and its children")
public record OrgUnitResponse(
        UUID id,
        @Schema(example = "BR-101") String code,
        String name,
        @Schema(example = "BRANCH") String unitType,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String city,
        String district,
        List<OrgUnitResponse> children) {

    public static OrgUnitResponse of(OrgUnit unit, List<OrgUnitResponse> children) {
        return new OrgUnitResponse(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getUnitType().getCode(),
                unit.getStatus(),
                unit.getEffectiveFrom(),
                unit.getEffectiveTo(),
                unit.getCity(),
                unit.getDistrict(),
                children);
    }
}
