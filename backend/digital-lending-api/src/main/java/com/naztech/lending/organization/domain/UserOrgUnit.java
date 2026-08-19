package com.naztech.lending.organization.domain;

import com.naztech.lending.auth.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A posting: one person, one unit they may act in.
 *
 * <p>An entity rather than a plain many-to-many because the relation carries
 * facts of its own - which posting is their home, when it was made and by whom.
 * A join table with columns is a table, and pretending otherwise loses them.
 */
@Entity
@Table(schema = "organization", name = "t_user_org_unit")
public class UserOrgUnit {

    @EmbeddedId
    private UserOrgUnitId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @MapsId("orgUnitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_unit_id", nullable = false, updatable = false)
    private OrgUnit orgUnit;

    /**
     * Where they sit, as opposed to where they may also act. A Sourcing Officer
     * covering three branches is based at exactly one of them, which the
     * database enforces with a partial unique index.
     */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "assigned_by", nullable = false, length = 64, updatable = false)
    private String assignedBy = "system";

    protected UserOrgUnit() {
        // for JPA
    }

    public UserOrgUnit(UserAccount user, OrgUnit orgUnit, boolean primary) {
        this.id = new UserOrgUnitId(user.getId(), orgUnit.getId());
        this.user = user;
        this.orgUnit = orgUnit;
        this.primary = primary;
    }

    public UserAccount getUser() {
        return user;
    }

    public OrgUnit getOrgUnit() {
        return orgUnit;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
