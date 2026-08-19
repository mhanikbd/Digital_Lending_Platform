package com.naztech.lending.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One unit of the bank: the institution, a zone, a region, a branch, or a head
 * office function.
 *
 * <p>Self-referencing, because that is what an organisation is. The alternative
 * - a table per kind - would have nine tables with the same six columns and a
 * new migration every time a bank reorganised.
 *
 * <p>Units are dated rather than deleted. A branch that closes stops taking
 * applications but keeps its loans, and a loan whose branch vanished from the
 * database is a loan nobody can explain.
 */
@Entity
@Table(schema = "organization", name = "t_org_unit")
public class OrgUnit {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_type_code", nullable = false)
    private OrgUnitType unitType;

    /** Null only for the root: a bank has no parent. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    /**
     * What the bank calls it. Branch codes appear on statements and in the core
     * banking system, so this is a business identifier and not a surrogate.
     */
    @Column(nullable = false, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "short_name", length = 60)
    private String shortName;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String district;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "system";

    @Version
    @Column(nullable = false)
    private long version;

    protected OrgUnit() {
        // for JPA
    }

    public OrgUnit(OrgUnitType unitType, OrgUnit parent, String code, String name) {
        this.unitType = unitType;
        this.parent = parent;
        this.code = code;
        this.name = name;
    }

    /** Open for business on the given day. */
    public boolean isOperatingOn(LocalDate day) {
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        return !day.isBefore(effectiveFrom) && (effectiveTo == null || !day.isAfter(effectiveTo));
    }

    public void describeContact(String addressLine, String city, String district) {
        this.addressLine = addressLine;
        this.city = city;
        this.district = district;
    }

    public UUID getId() {
        return id;
    }

    public OrgUnitType getUnitType() {
        return unitType;
    }

    public OrgUnit getParent() {
        return parent;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }
}
