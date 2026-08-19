package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Why the money is wanted.
 *
 * <p>A row, so a bank adds a purpose without a release. The specification names
 * six to begin with and says they must be configurable, which is the same
 * instruction it gives about product terms and eligibility criteria.
 */
@Entity
@Table(schema = "application", name = "t_loan_purpose")
public class LoanPurpose {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_bn", length = 160)
    private String nameBn;

    @Column(length = 255)
    private String description;

    /**
     * Whether the customer must say more. A medical loan may need to name the
     * treatment; a personal one needs nothing.
     */
    @Column(name = "requires_detail", nullable = false)
    private boolean requiresDetail;

    @Column(name = "display_order", nullable = false)
    private short displayOrder = 100;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LoanPurpose() {
        // for JPA
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNameBn() {
        return nameBn;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequiresDetail() {
        return requiresDetail;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public String getStatus() {
        return status;
    }
}
