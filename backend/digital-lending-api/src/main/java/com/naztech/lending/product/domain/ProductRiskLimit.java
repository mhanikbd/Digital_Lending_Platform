package com.naztech.lending.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** What one risk grade may borrow under one version of a product. */
@Entity
@Table(schema = "product", name = "t_product_risk_limit")
@IdClass(ProductRiskLimit.Key.class)
public class ProductRiskLimit {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_version_id", nullable = false, updatable = false)
    private LoanProductVersion productVersion;

    @Id
    @Column(name = "risk_profile", nullable = false, length = 20, updatable = false)
    private String riskProfile;

    @Column(name = "max_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal maxAmount;

    protected ProductRiskLimit() {
        // for JPA
    }

    /** A ceiling for one grade under one version. */
    static ProductRiskLimit of(LoanProductVersion version, String riskProfile,
                               BigDecimal maxAmount) {
        ProductRiskLimit limit = new ProductRiskLimit();
        limit.productVersion = version;
        limit.riskProfile = riskProfile;
        limit.maxAmount = maxAmount;
        return limit;
    }

    /** The same ceiling, attached to a new version. */
    static ProductRiskLimit copyOnto(ProductRiskLimit source, LoanProductVersion target) {
        ProductRiskLimit copy = new ProductRiskLimit();
        copy.productVersion = target;
        copy.riskProfile = source.riskProfile;
        copy.maxAmount = source.maxAmount;
        return copy;
    }

    public LoanProductVersion getProductVersion() {
        return productVersion;
    }

    public String getRiskProfile() {
        return riskProfile;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    /** Composite key: one limit per grade per version. */
    public static class Key implements Serializable {
        private UUID productVersion;
        private String riskProfile;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(productVersion, that.productVersion)
                    && Objects.equals(riskProfile, that.riskProfile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productVersion, riskProfile);
        }
    }
}
