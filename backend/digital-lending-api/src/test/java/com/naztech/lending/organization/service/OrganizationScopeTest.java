package com.naztech.lending.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naztech.lending.auth.domain.RoleScope;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.organization.domain.OrgUnit;
import com.naztech.lending.organization.repository.OrgUnitRepository;
import com.naztech.lending.organization.repository.OrgUnitTypeRepository;
import com.naztech.lending.organization.repository.UserOrgUnitRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * How much of the bank a person can see.
 *
 * <p>This is the rule the specification states as "BM sees their own branch, SO
 * sees their assigned branches, HOCRM sees a configured scope", and it is worth
 * pinning precisely: getting it wrong in the widening direction shows one
 * branch's loan book to another branch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationScopeTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();
    private static final UUID REGION = UUID.randomUUID();

    @Mock
    private OrgUnitRepository units;
    @Mock
    private OrgUnitTypeRepository unitTypes;
    @Mock
    private UserOrgUnitRepository postings;
    @Mock
    private UserAccountRepository users;

    @Test
    void aBranchScopedRoleSeesOnlyWhereTheyArePosted() {
        givenRoleScopes(RoleScope.BRANCH);
        when(postings.findOrgUnitIds(USER)).thenReturn(List.of(BRANCH_A));

        assertThat(service().visibleUnitIds(USER)).containsExactly(BRANCH_A);
        // No descendant walk: a branch has none that matter, and asking would be
        // a query per sign-in for nothing.
        verify(units, never()).findSubtreeIds(any());
    }

    @Test
    void aBranchScopedRoleWithSeveralPostingsSeesAllOfThem() {
        givenRoleScopes(RoleScope.BRANCH);
        when(postings.findOrgUnitIds(USER)).thenReturn(List.of(BRANCH_A, BRANCH_B));

        assertThat(service().visibleUnitIds(USER)).containsExactlyInAnyOrder(BRANCH_A, BRANCH_B);
    }

    @Test
    void aRegionScopedRoleSeesEverythingBeneathItsPostings() {
        givenRoleScopes(RoleScope.REGION);
        when(postings.findOrgUnitIds(USER)).thenReturn(List.of(REGION));
        when(units.findSubtreeIds(List.of(REGION))).thenReturn(List.of(REGION, BRANCH_A, BRANCH_B));

        assertThat(service().visibleUnitIds(USER))
                .containsExactlyInAnyOrder(REGION, BRANCH_A, BRANCH_B);
    }

    @Test
    void aHeadOfficeRoleSeesTheWholeBankWithoutBeingPostedAnywhere() {
        givenRoleScopes(RoleScope.HEAD_OFFICE);
        // Built before the stub below: creating them inside thenReturn(...) would
        // start a second stubbing while the first is still open.
        List<OrgUnit> everything = List.of(unit(BRANCH_A), unit(BRANCH_B), unit(REGION));
        when(units.findAll()).thenReturn(everything);

        assertThat(service().visibleUnitIds(USER))
                .containsExactlyInAnyOrder(BRANCH_A, BRANCH_B, REGION);
        verify(postings, never()).findOrgUnitIds(any());
    }

    @Test
    void severalRolesGrantTheWidestScopeRatherThanTheNarrowest() {
        givenRoleScopes(RoleScope.BRANCH, RoleScope.HEAD_OFFICE);
        List<OrgUnit> everything = List.of(unit(BRANCH_A), unit(REGION));
        when(units.findAll()).thenReturn(everything);

        // Adding a role must never take access away, which narrowing would do.
        assertThat(service().visibleUnitIds(USER)).containsExactlyInAnyOrder(BRANCH_A, REGION);
    }

    @Test
    void postedNowhereMeansAuthorisedOverNothing() {
        givenRoleScopes(RoleScope.BRANCH);
        when(postings.findOrgUnitIds(USER)).thenReturn(List.of());

        // The dangerous failure would be treating "no postings" as "no filter".
        assertThat(service().visibleUnitIds(USER)).isEmpty();
    }

    @Test
    void aRoleWithNoScopeAtAllIsTreatedAsTheNarrowest() {
        givenRoleScopes();
        when(postings.findOrgUnitIds(USER)).thenReturn(List.of(BRANCH_A));

        assertThat(service().visibleUnitIds(USER)).containsExactly(BRANCH_A);
    }

    private void givenRoleScopes(RoleScope... scopes) {
        when(users.findRoleScopes(USER)).thenReturn(List.of(scopes));
    }

    private OrganizationService service() {
        return new OrganizationService(units, unitTypes, postings, users);
    }

    private static OrgUnit unit(UUID id) {
        OrgUnit unit = org.mockito.Mockito.mock(OrgUnit.class);
        when(unit.getId()).thenReturn(id);
        return unit;
    }

    @Test
    void visibleIdsAreASetSoADuplicatedPostingCannotWidenAnything() {
        givenRoleScopes(RoleScope.REGION);
        when(postings.findOrgUnitIds(USER)).thenReturn(List.of(REGION, REGION));
        when(units.findSubtreeIds(List.of(REGION, REGION)))
                .thenReturn(List.of(REGION, BRANCH_A, BRANCH_A));

        Set<UUID> visible = service().visibleUnitIds(USER);

        assertThat(visible).containsExactlyInAnyOrder(REGION, BRANCH_A);
    }
}
