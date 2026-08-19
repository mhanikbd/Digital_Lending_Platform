package com.naztech.lending.auth.domain;

/**
 * How far a holder of a role can see once the organisation tree exists.
 *
 * <p>Recorded from Milestone 6 so that Milestone 7 has something to attach to,
 * and deliberately read by nothing until then: scoping a query by branch is
 * meaningless while there are no branches.
 */
public enum RoleScope {
    BRANCH,
    REGION,
    HEAD_OFFICE
}
