package com.naztech.lending.organization;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.organization.dto.OrgScopeResponse;
import com.naztech.lending.organization.dto.OrgUnitResponse;
import com.naztech.lending.organization.dto.OrgUnitTypeResponse;
import com.naztech.lending.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bank's own shape.
 *
 * <p>Reads only. Building the hierarchy is an administration screen, and the
 * audit trail a change to it requires does not exist yet - moving a branch
 * between regions silently would be a poor way to discover that.
 */
@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "The bank hierarchy and the scope a signed-in user has over it")
public class OrganizationController {

    private final OrganizationService organization;

    public OrganizationController(OrganizationService organization) {
        this.organization = organization;
    }

    @GetMapping("/unit-types")
    @PreAuthorize("hasAuthority('organization.view')")
    @Operation(summary = "The kinds of unit this bank is made of",
            description = "A catalogue rather than an enum, so a bank can add a kind without a "
                    + "release. Requires the organization.view permission.")
    public ApiResponse<List<OrgUnitTypeResponse>> unitTypes() {
        return ApiResponse.success(organization.unitTypes());
    }

    @GetMapping("/units")
    @PreAuthorize("hasAuthority('organization.view')")
    @Operation(summary = "The hierarchy, as a tree",
            description = "Every unit with its children nested beneath it. Requires the "
                    + "organization.view permission.")
    public ApiResponse<List<OrgUnitResponse>> units() {
        return ApiResponse.success(organization.tree());
    }

    @GetMapping("/my-scope")
    @Operation(summary = "How much of the organisation the caller can see",
            description = "The widest scope their roles grant, the units they are posted to, and "
                    + "every unit those two facts together make visible. Needs no permission "
                    + "beyond being signed in: it describes the caller to themselves.")
    public ApiResponse<OrgScopeResponse> myScope(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(organization.scopeOf(UUID.fromString(jwt.getSubject())));
    }
}
