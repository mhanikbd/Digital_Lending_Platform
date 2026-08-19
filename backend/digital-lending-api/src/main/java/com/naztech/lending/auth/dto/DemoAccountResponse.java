package com.naztech.lending.auth.dto;

import com.naztech.lending.auth.DemoAccounts.DemoAccount;
import com.naztech.lending.auth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One demonstration account, as the sign-in page offers it.
 *
 * <p>Carries the password. That is the whole point of the endpoint, and it is
 * only ever served under the local profile - see
 * {@code LocalDemoAccountController} for why the three guards that make it
 * acceptable have to hold together.
 *
 * <p>The role's name and scope are read from the database rather than repeated
 * in the roster, so a bank that renames a role sees the new name on the card.
 */
@Schema(description = "A seeded local account, offered for one-click sign-in")
public record DemoAccountResponse(
        @Schema(example = "EMP-10002") String username,
        @Schema(example = "Demo#Local1") String password,
        @Schema(example = "Nasima Haque") String displayName,
        @Schema(example = "BM") String roleCode,
        @Schema(example = "Branch Manager") String roleName,
        @Schema(description = "How far the role sees", example = "BRANCH") String scope,
        @Schema(description = "The unit they are posted to", example = "BR-101") String orgUnitCode,
        @Schema(example = "Branch Manager, Gulshan. Sees only that branch's customers.")
        String note) {

    /** {@code role} is null when the catalogue does not hold it, which shows as the code. */
    public static DemoAccountResponse of(DemoAccount account, String password, Role role) {
        return new DemoAccountResponse(
                account.username(),
                password,
                account.displayName(),
                account.roleCode(),
                role != null ? role.getName() : account.roleCode(),
                role != null ? role.getScopeLevel().name() : null,
                account.orgUnitCode(),
                account.note());
    }
}
