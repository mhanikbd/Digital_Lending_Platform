package com.naztech.lending.product.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A product the bank markets.
 *
 * <p>Deliberately thin. Nothing here gets repriced: the rate, the limits, the
 * fees and the tenures are all on the version, so that changing them cannot
 * disturb an application already in flight.
 */
@Entity
@Table(schema = "product", name = "t_loan_product")
public class LoanProduct {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** The bank operates in Bangladesh and its customers do not all read English. */
    @Column(name = "name_bn", length = 160)
    private String nameBn;

    @Column(name = "product_type", nullable = false, length = 30)
    private String productType;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("versionNo ASC")
    private Set<LoanProductVersion> versions = new LinkedHashSet<>();

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

    protected LoanProduct() {
        // for JPA
    }

    /**
     * Registers a product.
     *
     * <p>Only identity. The product is not sellable until a version is drafted
     * against it and activated, which is deliberate: registering a name and
     * committing to terms are separate decisions, and a bank that has agreed the
     * first has usually not finished arguing about the second.
     */
    public static LoanProduct of(String code, String name, String nameBn, String productType,
                                 String category, String description, String author) {
        LoanProduct product = new LoanProduct();
        product.code = code;
        product.name = name;
        product.nameBn = nameBn;
        product.productType = productType;
        product.category = category;
        product.description = description;
        product.createdBy = author;
        product.updatedBy = author;
        return product;
    }

    /** Attaches a version, setting both sides of the association. */
    public LoanProduct with(LoanProductVersion version) {
        versions.add(version);
        return this;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * The version a new application would be judged under.
     *
     * <p>At most one can qualify: the database carries a partial unique index
     * that permits a single ACTIVE version per product, because "which terms
     * apply" must not be a question with two answers.
     */
    public Optional<LoanProductVersion> sellableVersionOn(LocalDate day) {
        return versions.stream().filter(candidate -> candidate.isSellableOn(day)).findFirst();
    }

    public UUID getId() {
        return id;
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

    public String getProductType() {
        return productType;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public Set<LoanProductVersion> getVersions() {
        return versions;
    }
}
