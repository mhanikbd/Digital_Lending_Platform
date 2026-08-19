package com.naztech.lending.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What of the organisation the signed-in person can see, and why.
 *
 * <p>The reason is returned alongside the answer on purpose. "You can see three
 * branches" is unactionable on its own; "your role is branch-scoped and you are
 * posted to three branches" tells an administrator exactly which of the two
 * facts to change.
 */
@Schema(description = "The organisational scope of the signed-in user")
public record OrgScopeResponse(
        @Schema(description = "Widest scope any of their roles grants", example = "BRANCH")
        String scopeLevel,
        @Schema(description = "Units they are posted to") List<PostingResponse> postings,
        @Schema(description = "Codes of every unit they may act on, postings and anything "
                + "beneath them once the scope allows it")
        List<String> visibleUnitCodes) {

    /** One posting: a unit, and whether it is where they are based. */
    @Schema(description = "A posting")
    public record PostingResponse(
            @Schema(example = "BR-101") String code,
            String name,
            @Schema(example = "BRANCH") String unitType,
            @Schema(description = "Their home posting, of which there is at most one")
            boolean primary) {
    }
}
