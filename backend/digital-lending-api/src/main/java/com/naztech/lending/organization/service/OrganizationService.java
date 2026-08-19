package com.naztech.lending.organization.service;

import com.naztech.lending.auth.domain.RoleScope;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.organization.domain.OrgUnit;
import com.naztech.lending.organization.domain.UserOrgUnit;
import com.naztech.lending.organization.dto.OrgScopeResponse;
import com.naztech.lending.organization.dto.OrgUnitResponse;
import com.naztech.lending.organization.dto.OrgUnitTypeResponse;
import com.naztech.lending.organization.repository.OrgUnitRepository;
import com.naztech.lending.organization.repository.OrgUnitTypeRepository;
import com.naztech.lending.organization.repository.UserOrgUnitRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the bank's own shape, and works out how much of it a person can see.
 *
 * <p>The second part is what the specification asks for when it says branch-level
 * and head-office-level authorisation must both be supported. A Branch Manager
 * sees their branch, a Relationship Manager sees the region beneath them, and a
 * Head of Credit Risk Management sees the bank. None of that is written as a
 * check on a role name: the width comes from the role's scope level, which is a
 * column, and the starting points come from the person's postings, which are
 * rows.
 */
@Service
public class OrganizationService {

    private final OrgUnitRepository units;
    private final OrgUnitTypeRepository unitTypes;
    private final UserOrgUnitRepository postings;
    private final UserAccountRepository users;

    public OrganizationService(OrgUnitRepository units,
                               OrgUnitTypeRepository unitTypes,
                               UserOrgUnitRepository postings,
                               UserAccountRepository users) {
        this.units = units;
        this.unitTypes = unitTypes;
        this.postings = postings;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<OrgUnitTypeResponse> unitTypes() {
        return unitTypes.findAllByOrderByHierarchyLevelAscCodeAsc().stream()
                .map(OrgUnitTypeResponse::from)
                .toList();
    }

    /**
     * The whole hierarchy, assembled into a tree.
     *
     * <p>Built in memory from one query rather than by recursing into the
     * database. A bank has hundreds of units, not millions, and one round trip
     * that sorts itself out here beats a query per level.
     */
    @Transactional(readOnly = true)
    public List<OrgUnitResponse> tree() {
        List<OrgUnit> all = units.findAllByOrderByCodeAsc();

        Map<UUID, List<OrgUnit>> childrenByParent = new LinkedHashMap<>();
        List<OrgUnit> roots = new ArrayList<>();
        for (OrgUnit unit : all) {
            if (unit.getParent() == null) {
                roots.add(unit);
            } else {
                childrenByParent
                        .computeIfAbsent(unit.getParent().getId(), key -> new ArrayList<>())
                        .add(unit);
            }
        }

        return roots.stream().map(root -> assemble(root, childrenByParent)).toList();
    }

    /** What this person can see, and the two facts that decide it. */
    @Transactional(readOnly = true)
    public OrgScopeResponse scopeOf(UUID userId) {
        RoleScope widest = widestScope(users.findRoleScopes(userId));
        List<UserOrgUnit> held = postings.findByUserId(userId);

        List<OrgScopeResponse.PostingResponse> postingResponses = held.stream()
                .map(posting -> new OrgScopeResponse.PostingResponse(
                        posting.getOrgUnit().getCode(),
                        posting.getOrgUnit().getName(),
                        posting.getOrgUnit().getUnitType().getCode(),
                        posting.isPrimary()))
                .sorted(Comparator.comparing(OrgScopeResponse.PostingResponse::code))
                .toList();

        return new OrgScopeResponse(
                widest.name(),
                postingResponses,
                visibleUnitCodes(widest, held));
    }

    /**
     * The widest scope this person holds, for callers that need the reason
     * rather than the resulting set - a head office reader is not filtered at
     * all, which is different from being filtered to everything.
     */
    @Transactional(readOnly = true)
    public RoleScope widestScopeOf(UUID userId) {
        return widestScope(users.findRoleScopes(userId));
    }

    /**
     * The unit ids this person may act on.
     *
     * <p>Exposed for the modules that will filter their own queries by it, from
     * the loan application queue onwards. Returning ids rather than a predicate
     * keeps the decision in one place instead of in every repository.
     */
    @Transactional(readOnly = true)
    public Set<UUID> visibleUnitIds(UUID userId) {
        RoleScope widest = widestScope(users.findRoleScopes(userId));
        if (widest == RoleScope.HEAD_OFFICE) {
            return units.findAll().stream().map(OrgUnit::getId).collect(java.util.stream.Collectors.toSet());
        }

        List<UUID> postedTo = postings.findOrgUnitIds(userId);
        if (postedTo.isEmpty()) {
            // Posted nowhere means authorised over nothing. Failing closed here
            // matters: an unposted account must not quietly see the whole bank.
            return Set.of();
        }
        List<UUID> reachable = widest == RoleScope.REGION
                ? units.findSubtreeIds(postedTo)
                : postedTo;
        return Set.copyOf(reachable);
    }

    /**
     * A person holding several roles gets the widest of them. Narrowing to the
     * least would make adding a role able to take access away, which is not how
     * anyone expects roles to behave.
     */
    private static RoleScope widestScope(List<RoleScope> scopes) {
        if (scopes.contains(RoleScope.HEAD_OFFICE)) {
            return RoleScope.HEAD_OFFICE;
        }
        if (scopes.contains(RoleScope.REGION)) {
            return RoleScope.REGION;
        }
        return RoleScope.BRANCH;
    }

    private List<String> visibleUnitCodes(RoleScope widest, List<UserOrgUnit> held) {
        if (widest == RoleScope.HEAD_OFFICE) {
            return units.findAllByOrderByCodeAsc().stream().map(OrgUnit::getCode).toList();
        }
        if (held.isEmpty()) {
            return List.of();
        }
        List<UUID> roots = held.stream().map(posting -> posting.getOrgUnit().getId()).toList();
        Set<UUID> reachable = widest == RoleScope.REGION
                ? Set.copyOf(units.findSubtreeIds(roots))
                : Set.copyOf(roots);

        return units.findAllByOrderByCodeAsc().stream()
                .filter(unit -> reachable.contains(unit.getId()))
                .map(OrgUnit::getCode)
                .toList();
    }

    private OrgUnitResponse assemble(OrgUnit unit, Map<UUID, List<OrgUnit>> childrenByParent) {
        List<OrgUnitResponse> children = childrenByParent
                .getOrDefault(unit.getId(), List.of()).stream()
                .map(child -> assemble(child, childrenByParent))
                .toList();
        return OrgUnitResponse.of(unit, children);
    }
}
