package com.naztech.lending.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One kind of unit this bank is made of.
 *
 * <p>A catalogue rather than an enum, so a bank that opens a tenth kind of unit
 * next year adds a row instead of waiting for a release.
 */
@Entity
@Table(schema = "organization", name = "t_org_unit_type")
public class OrgUnitType {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    /** The parent a unit of this type normally hangs from. Advisory only. */
    @Column(name = "parent_type_code", length = 30)
    private String parentTypeCode;

    @Column(name = "hierarchy_level", nullable = false)
    private short hierarchyLevel;

    /**
     * Whether this type serves customers over a counter. The branch-level versus
     * head-office-level distinction rests on this, so a bank adding a new
     * customer-facing type is not a code change.
     */
    @Column(name = "is_customer_facing", nullable = false)
    private boolean customerFacing;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OrgUnitType() {
        // for JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParentTypeCode() {
        return parentTypeCode;
    }

    public short getHierarchyLevel() {
        return hierarchyLevel;
    }

    public boolean isCustomerFacing() {
        return customerFacing;
    }
}
