package com.naztech.lending.product.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The terms a product is sold on.
 *
 * <p>Everything a lending decision touches lives here rather than on the product,
 * so that repricing produces a new row instead of editing the one an approved
 * application is still pointing at. An application records the version id it was
 * judged under and keeps it for the life of the loan.
 *
 * <p>Rates are percentages per annum as a product sheet states them: 9.000000 is
 * nine percent. They are converted to fractions once, at the point of
 * calculation, rather than stored as fractions half the code forgets to scale.
 */
@Entity
@Table(schema = "product", name = "t_loan_product_version")
public class LoanProductVersion {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private LoanProduct product;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "customer_segment", nullable = false, length = 40)
    private String customerSegment = "ANY";

    @Column(nullable = false)
    private boolean secured;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Column(name = "min_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "min_tenure_months", nullable = false)
    private short minTenureMonths;

    @Column(name = "max_tenure_months", nullable = false)
    private short maxTenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 30)
    private InterestMethod interestMethod;

    @Column(name = "interest_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "grace_period_days", nullable = false)
    private short gracePeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 20)
    private RepaymentFrequency repaymentFrequency = RepaymentFrequency.MONTHLY;

    @Column(name = "collateral_required", nullable = false)
    private boolean collateralRequired;

    @Column(name = "guarantor_required", nullable = false)
    private boolean guarantorRequired;

    @Column(name = "income_multiple", precision = 9, scale = 4)
    private BigDecimal incomeMultiple;

    @Column(name = "max_dbr", precision = 5, scale = 4)
    private BigDecimal maxDbr;

    @Column(name = "regulatory_max_amount", precision = 20, scale = 4)
    private BigDecimal regulatoryMaxAmount;

    /**
     * The share of the eligible maximum actually offered.
     *
     * <p>A customer lent their absolute ceiling has no headroom left, and banks
     * do not lead with it. The share is marketing policy, so it is configured
     * per version rather than assumed by the amount engine.
     */
    @Column(name = "recommended_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal recommendedRatio = new BigDecimal("0.7000");

    /**
     * A ceiling on what the borrower may owe in total, this loan included.
     *
     * <p>A concentration control, not an affordability one - affordability is
     * what {@link #maxDbr} measures. Null means the product sets no such
     * ceiling, which is the ordinary case: a personal loan is not normally
     * refused to somebody for holding a mortgage they are comfortably servicing.
     */
    @Column(name = "max_total_exposure", precision = 20, scale = 4)
    private BigDecimal maxTotalExposure;

    /**
     * The tenures actually offered, which is not every month between the bounds.
     * A plain set of numbers rather than an entity: the row has no other facts.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(schema = "product", name = "t_product_tenure",
            joinColumns = @JoinColumn(name = "product_version_id"))
    @Column(name = "tenure_months", nullable = false)
    private Set<Short> tenures = new LinkedHashSet<>();

    @OneToMany(mappedBy = "productVersion", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<ProductFee> fees = new LinkedHashSet<>();

    @OneToMany(mappedBy = "productVersion", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<ProductRiskLimit> riskLimits = new LinkedHashSet<>();

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

    protected LoanProductVersion() {
        // for JPA
    }

    /**
     * The first version of a product.
     *
     * <p>Starts as a draft, like every other version. The only thing that makes
     * it special is that there is nothing to copy from.
     *
     * <p>Takes only the terms without which a loan cannot be quoted at all. The
     * limit parameters, the fees and the risk ceilings are optional, and are
     * added afterwards - a product may perfectly well cap nothing but its own
     * maximum.
     */
    public static LoanProductVersion initial(LoanProduct product, LocalDate effectiveFrom,
                                             String currency, BigDecimal minAmount,
                                             BigDecimal maxAmount, Set<Short> tenures,
                                             InterestMethod interestMethod, BigDecimal interestRate,
                                             RepaymentFrequency frequency, String author) {
        if (tenures == null || tenures.isEmpty()) {
            throw new IllegalArgumentException("A product must offer at least one tenure");
        }
        LoanProductVersion version = new LoanProductVersion();
        version.product = product;
        version.versionNo = 1;
        version.status = VersionStatus.DRAFT;
        version.effectiveFrom = effectiveFrom;
        version.currency = currency;
        version.minAmount = minAmount;
        version.maxAmount = maxAmount;
        version.tenures = new LinkedHashSet<>(tenures);
        version.minTenureMonths = tenures.stream().min(Short::compare).orElseThrow();
        version.maxTenureMonths = tenures.stream().max(Short::compare).orElseThrow();
        version.interestMethod = interestMethod;
        version.interestRate = interestRate;
        version.repaymentFrequency = frequency;
        version.createdBy = author;
        version.updatedBy = author;
        return version;
    }

    /**
     * Sets the parameters the amount engine reads.
     *
     * <p>Any of them may be null, and null means the product does not cap
     * lending that way - which the engine reports as a factor that did not
     * apply rather than as a limit of zero.
     */
    public LoanProductVersion withLimits(BigDecimal incomeMultiple, BigDecimal maxDbr,
                                         BigDecimal regulatoryMaxAmount,
                                         BigDecimal recommendedRatio,
                                         BigDecimal maxTotalExposure) {
        this.incomeMultiple = incomeMultiple;
        this.maxDbr = maxDbr;
        this.regulatoryMaxAmount = regulatoryMaxAmount;
        this.maxTotalExposure = maxTotalExposure;
        if (recommendedRatio != null) {
            this.recommendedRatio = recommendedRatio;
        }
        return this;
    }

    /** Adds a fee, setting both sides of the association. */
    public LoanProductVersion withFee(ProductFee fee) {
        fee.attachTo(this);
        fees.add(fee);
        return this;
    }

    /** Adds a per-grade ceiling, setting both sides of the association. */
    public LoanProductVersion withRiskLimit(String riskProfile, BigDecimal maxAmount) {
        riskLimits.add(ProductRiskLimit.of(this, riskProfile, maxAmount));
        return this;
    }

    /**
     * A new draft, carrying everything forward from an existing version.
     *
     * <p>This is what repricing means here. The live version is never edited -
     * applications are pointing at it - so a change produces a copy, and the
     * copy starts identical so that an amendment is only the fields somebody
     * actually meant to change.
     *
     * <p>Fees, tenures and risk limits are copied, not shared. Sharing them
     * would let an edit on version 2 reach back and alter what version 1
     * charged people who are still repaying under it.
     */
    public static LoanProductVersion draftFrom(LoanProductVersion source, int versionNo,
                                               LocalDate effectiveFrom, String author) {
        LoanProductVersion draft = new LoanProductVersion();
        draft.product = source.product;
        draft.versionNo = versionNo;
        draft.status = VersionStatus.DRAFT;
        draft.effectiveFrom = effectiveFrom;
        draft.effectiveTo = null;
        draft.customerSegment = source.customerSegment;
        draft.secured = source.secured;
        draft.currency = source.currency;
        draft.minAmount = source.minAmount;
        draft.maxAmount = source.maxAmount;
        draft.minTenureMonths = source.minTenureMonths;
        draft.maxTenureMonths = source.maxTenureMonths;
        draft.interestMethod = source.interestMethod;
        draft.interestRate = source.interestRate;
        draft.gracePeriodDays = source.gracePeriodDays;
        draft.repaymentFrequency = source.repaymentFrequency;
        draft.collateralRequired = source.collateralRequired;
        draft.guarantorRequired = source.guarantorRequired;
        draft.incomeMultiple = source.incomeMultiple;
        draft.maxDbr = source.maxDbr;
        draft.regulatoryMaxAmount = source.regulatoryMaxAmount;
        draft.recommendedRatio = source.recommendedRatio;
        draft.maxTotalExposure = source.maxTotalExposure;
        draft.tenures = new LinkedHashSet<>(source.tenures);
        draft.createdBy = author;
        draft.updatedBy = author;
        source.fees.forEach(fee -> draft.fees.add(ProductFee.copyOnto(fee, draft)));
        source.riskLimits.forEach(limit ->
                draft.riskLimits.add(ProductRiskLimit.copyOnto(limit, draft)));
        return draft;
    }

    /**
     * Amends a draft.
     *
     * <p>Every argument is optional; a null leaves the field as the version it
     * was copied from had it. Refuses outright on anything that is not a draft,
     * because the whole point of the version split is that live terms do not
     * move.
     */
    public void amend(BigDecimal newMinAmount, BigDecimal newMaxAmount,
                      InterestMethod newMethod, BigDecimal newRate,
                      BigDecimal newIncomeMultiple, BigDecimal newMaxDbr,
                      BigDecimal newRegulatoryMax, BigDecimal newRecommendedRatio,
                      BigDecimal newMaxTotalExposure, Set<Short> newTenures, String author) {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                    "Version %d is %s and cannot be edited".formatted(versionNo, status));
        }
        if (newMinAmount != null) {
            this.minAmount = newMinAmount;
        }
        if (newMaxAmount != null) {
            this.maxAmount = newMaxAmount;
        }
        if (newMethod != null) {
            this.interestMethod = newMethod;
        }
        if (newRate != null) {
            this.interestRate = newRate;
        }
        if (newIncomeMultiple != null) {
            this.incomeMultiple = newIncomeMultiple;
        }
        if (newMaxDbr != null) {
            this.maxDbr = newMaxDbr;
        }
        if (newRegulatoryMax != null) {
            this.regulatoryMaxAmount = newRegulatoryMax;
        }
        if (newRecommendedRatio != null) {
            this.recommendedRatio = newRecommendedRatio;
        }
        if (newMaxTotalExposure != null) {
            this.maxTotalExposure = newMaxTotalExposure;
        }
        if (newTenures != null && !newTenures.isEmpty()) {
            this.tenures = new LinkedHashSet<>(newTenures);
            this.minTenureMonths = newTenures.stream().min(Short::compare).orElseThrow();
            this.maxTenureMonths = newTenures.stream().max(Short::compare).orElseThrow();
        }
        this.updatedBy = author;
        this.updatedAt = Instant.now();
    }

    /**
     * Puts this version on sale from the given day.
     *
     * <p>Only a draft can be activated. The database holds a partial unique
     * index that permits one live version per product, so the caller must
     * retire the incumbent first - and being refused by the index is the
     * intended outcome if it forgets.
     */
    public void activate(LocalDate from, String author) {
        if (status != VersionStatus.DRAFT) {
            throw new IllegalStateException(
                    "Version %d is %s and cannot be activated".formatted(versionNo, status));
        }
        this.status = VersionStatus.ACTIVE;
        this.effectiveFrom = from;
        this.updatedBy = author;
        this.updatedAt = Instant.now();
    }

    /**
     * Takes this version off sale.
     *
     * <p>The row stays. Applications reference it, loans were written under it,
     * and a retired version is the only record of what those terms were.
     */
    public void retire(LocalDate on, String author) {
        if (status != VersionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Version %d is %s and is not on sale".formatted(versionNo, status));
        }
        this.status = VersionStatus.RETIRED;
        this.effectiveTo = on;
        this.updatedBy = author;
        this.updatedAt = Instant.now();
    }

    /** True when this version may be lent against on the given day. */
    public boolean isSellableOn(LocalDate day) {
        if (!status.isSellable()) {
            return false;
        }
        return !day.isBefore(effectiveFrom) && (effectiveTo == null || !day.isAfter(effectiveTo));
    }

    /** True when the requested amount sits inside the product's own bounds. */
    public boolean acceptsAmount(BigDecimal amount) {
        return amount != null
                && amount.compareTo(minAmount) >= 0
                && amount.compareTo(maxAmount) <= 0;
    }

    /**
     * True when the tenure is one the product actually offers.
     *
     * <p>Falls back to the bounds only when no discrete tenures are configured,
     * so a product that lists 3, 6, 9 and 12 does not quietly accept 7.
     */
    public boolean acceptsTenure(int months) {
        if (!tenures.isEmpty()) {
            return tenures.contains((short) months);
        }
        return months >= minTenureMonths && months <= maxTenureMonths;
    }

    /** The offered tenures in ascending order, for a client to render as choices. */
    public List<Integer> offeredTenures() {
        if (tenures.isEmpty()) {
            return java.util.stream.IntStream
                    .rangeClosed(minTenureMonths, maxTenureMonths).boxed().toList();
        }
        return tenures.stream().map(Short::intValue).sorted().toList();
    }

    /** The cap for a risk grade, when the product sets one. */
    public Optional<BigDecimal> riskLimitFor(String riskProfile) {
        return riskLimits.stream()
                .filter(limit -> limit.getRiskProfile().equals(riskProfile))
                .findFirst()
                .map(ProductRiskLimit::getMaxAmount);
    }

    /** Fees taken at the given point, which is what decides where they land. */
    public List<ProductFee> feesCollectedAt(FeeCollectionPoint point) {
        return fees.stream().filter(fee -> fee.getCollectedAt() == point).toList();
    }

    public UUID getId() {
        return id;
    }

    public LoanProduct getProduct() {
        return product;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getCustomerSegment() {
        return customerSegment;
    }

    public boolean isSecured() {
        return secured;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public short getMinTenureMonths() {
        return minTenureMonths;
    }

    public short getMaxTenureMonths() {
        return maxTenureMonths;
    }

    public InterestMethod getInterestMethod() {
        return interestMethod;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public short getGracePeriodDays() {
        return gracePeriodDays;
    }

    public RepaymentFrequency getRepaymentFrequency() {
        return repaymentFrequency;
    }

    public boolean isCollateralRequired() {
        return collateralRequired;
    }

    public boolean isGuarantorRequired() {
        return guarantorRequired;
    }

    public BigDecimal getIncomeMultiple() {
        return incomeMultiple;
    }

    public BigDecimal getMaxDbr() {
        return maxDbr;
    }

    public BigDecimal getRegulatoryMaxAmount() {
        return regulatoryMaxAmount;
    }

    public BigDecimal getRecommendedRatio() {
        return recommendedRatio;
    }

    public BigDecimal getMaxTotalExposure() {
        return maxTotalExposure;
    }

    public Set<ProductFee> getFees() {
        return fees;
    }

    public Set<ProductRiskLimit> getRiskLimits() {
        return riskLimits;
    }
}
