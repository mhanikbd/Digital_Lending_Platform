package com.naztech.lending.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A document the customer proved themselves with.
 *
 * <p>Unverified until an authority has actually been asked, whatever the
 * customer typed. The KYC module from Milestone 10 is what flips that, and
 * treating a typed number as proof is the mistake this flag exists to prevent.
 */
@Entity
@Table(schema = "customer", name = "t_customer_identification")
public class CustomerIdentification {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 30)
    private IdentificationType idType;

    @Column(name = "id_number", nullable = false, length = 60)
    private String idNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issue_place", length = 120)
    private String issuePlace;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected CustomerIdentification() {
        // for JPA
    }

    public CustomerIdentification(IdentificationType idType, String idNumber) {
        this.idType = idType;
        this.idNumber = idNumber;
    }

    /** Set by Customer.addIdentification so both ends of the relation agree. */
    void attachTo(Customer customer) {
        this.customer = customer;
    }

    /** Recorded once an authority has confirmed the document, not before. */
    public void markVerified(Instant now) {
        this.verified = true;
        this.verifiedAt = now;
        this.updatedAt = now;
    }

    /** A document with no expiry, such as a TIN, never expires. */
    public boolean isExpiredOn(LocalDate day) {
        return expiryDate != null && expiryDate.isBefore(day);
    }

    public void dateIt(LocalDate issueDate, LocalDate expiryDate, String issuePlace) {
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.issuePlace = issuePlace;
    }

    public UUID getId() {
        return id;
    }

    public IdentificationType getIdType() {
        return idType;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getIssuePlace() {
        return issuePlace;
    }

    public boolean isVerified() {
        return verified;
    }
}
