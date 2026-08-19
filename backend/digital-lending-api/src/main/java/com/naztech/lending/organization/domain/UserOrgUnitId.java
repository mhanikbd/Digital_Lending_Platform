package com.naztech.lending.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key of a posting: one person in one unit. */
@Embeddable
public class UserOrgUnitId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    protected UserOrgUnitId() {
        // for JPA
    }

    public UserOrgUnitId(UUID userId, UUID orgUnitId) {
        this.userId = userId;
        this.orgUnitId = orgUnitId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrgUnitId() {
        return orgUnitId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserOrgUnitId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(orgUnitId, that.orgUnitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, orgUnitId);
    }
}
