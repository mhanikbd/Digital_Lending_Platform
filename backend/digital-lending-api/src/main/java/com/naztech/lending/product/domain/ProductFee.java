package com.naztech.lending.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One charge a product levies: processing, insurance, a late payment penalty.
 *
 * <p>The fee knows how to compute itself, because the alternative is a switch on
 * the calculation method repeated in every caller. VAT is charged on the fee and
 * not on the loan, and the rate is stored rather than assumed - fifteen percent
 * is the Bangladeshi figure today, not a constant of nature.
 */
@Entity
@Table(schema = "product", name = "t_product_fee")
public class ProductFee {

    /** Money is presented to two places; the fourth is kept only for storage. */
    private static final int MONEY_SCALE = 2;

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_version_id", nullable = false, updatable = false)
    private LoanProductVersion productVersion;

    @Column(name = "fee_code", nullable = false, length = 30)
    private String feeCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false, length = 30)
    private FeeCalculationMethod calculationMethod;

    @Column(name = "flat_amount", precision = 20, scale = 4)
    private BigDecimal flatAmount;

    @Column(precision = 9, scale = 6)
    private BigDecimal rate;

    @Column(name = "vat_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "collected_at", nullable = false, length = 20)
    private FeeCollectionPoint collectedAt = FeeCollectionPoint.DISBURSEMENT;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ProductFee() {
        // for JPA
    }

    /** A fee charged as a share of the principal. */
    public static ProductFee percentage(String feeCode, String name, BigDecimal rate,
                                        BigDecimal vatRate, FeeCollectionPoint collectedAt) {
        ProductFee fee = new ProductFee();
        fee.feeCode = feeCode;
        fee.name = name;
        fee.calculationMethod = FeeCalculationMethod.PERCENT_OF_PRINCIPAL;
        fee.rate = rate;
        fee.vatRate = vatRate == null ? BigDecimal.ZERO : vatRate;
        fee.collectedAt = collectedAt;
        return fee;
    }

    /** A fee of a fixed amount, whatever the loan is for. */
    public static ProductFee flat(String feeCode, String name, BigDecimal amount,
                                  BigDecimal vatRate, FeeCollectionPoint collectedAt) {
        ProductFee fee = new ProductFee();
        fee.feeCode = feeCode;
        fee.name = name;
        fee.calculationMethod = FeeCalculationMethod.FLAT;
        fee.flatAmount = amount;
        fee.vatRate = vatRate == null ? BigDecimal.ZERO : vatRate;
        fee.collectedAt = collectedAt;
        return fee;
    }

    void attachTo(LoanProductVersion owner) {
        this.productVersion = owner;
    }

    /**
     * The same fee, attached to a new version.
     *
     * <p>Copied rather than shared: a fee row belongs to exactly one version, so
     * that amending it on version 2 cannot reach back and change what version 1
     * charged the people already repaying under it.
     */
    static ProductFee copyOnto(ProductFee source, LoanProductVersion target) {
        ProductFee copy = new ProductFee();
        copy.productVersion = target;
        copy.feeCode = source.feeCode;
        copy.name = source.name;
        copy.calculationMethod = source.calculationMethod;
        copy.flatAmount = source.flatAmount;
        copy.rate = source.rate;
        copy.vatRate = source.vatRate;
        copy.collectedAt = source.collectedAt;
        copy.mandatory = source.mandatory;
        return copy;
    }

    /**
     * The fee itself, before VAT, rounded half-up to two places.
     *
     * <p>A percentage fee is a share of principal; a flat fee ignores it. The
     * database constraint guarantees the column the method needs is populated,
     * so neither branch has to defend against a null.
     */
    public BigDecimal baseAmountOn(BigDecimal principal) {
        BigDecimal base = switch (calculationMethod) {
            case FLAT -> flatAmount;
            case PERCENT_OF_PRINCIPAL -> principal
                    .multiply(rate)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        };
        return base.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** VAT on the fee, rounded the same way, so base + VAT is exactly the total. */
    public BigDecimal vatOn(BigDecimal principal) {
        return baseAmountOn(principal)
                .multiply(vatRate)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** What the customer actually pays for this fee. */
    public BigDecimal totalOn(BigDecimal principal) {
        return baseAmountOn(principal).add(vatOn(principal));
    }

    public UUID getId() {
        return id;
    }

    public LoanProductVersion getProductVersion() {
        return productVersion;
    }

    public String getFeeCode() {
        return feeCode;
    }

    public String getName() {
        return name;
    }

    public FeeCalculationMethod getCalculationMethod() {
        return calculationMethod;
    }

    public BigDecimal getFlatAmount() {
        return flatAmount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public FeeCollectionPoint getCollectedAt() {
        return collectedAt;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
