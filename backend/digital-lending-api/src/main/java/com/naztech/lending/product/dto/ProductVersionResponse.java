package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.LoanProductVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One version of a product, in full.
 *
 * <p>Includes the limit parameters - the income multiple, the debt burden
 * ratio, the regulatory ceiling. They are not secrets: a banker who cannot see
 * why the platform sized a loan the way it did cannot defend the decision to
 * the customer standing in front of them.
 */
@Schema(description = "The terms of one product version")
public record ProductVersionResponse(

        UUID id,
        @Schema(example = "1") int versionNo,
        @Schema(example = "ACTIVE") String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,

        @Schema(example = "ANY") String customerSegment,
        boolean secured,
        @Schema(example = "BDT") String currency,

        @Schema(example = "5000.0000") BigDecimal minAmount,
        @Schema(example = "50000.0000") BigDecimal maxAmount,
        @Schema(description = "Tenures offered, in months", example = "[3, 6, 9, 12]")
        List<Integer> tenures,

        @Schema(example = "REDUCING_BALANCE") String interestMethod,
        @Schema(description = "Percent per annum", example = "9.000000") BigDecimal interestRate,
        @Schema(example = "MONTHLY") String repaymentFrequency,
        @Schema(example = "0") int gracePeriodDays,

        boolean collateralRequired,
        boolean guarantorRequired,

        @Schema(description = "Months of income the product will lend", example = "10.0000")
        BigDecimal incomeMultiple,
        @Schema(description = "Share of income that may service debt, so 0.5000 is fifty percent",
                example = "0.5000") BigDecimal maxDbr,
        @Schema(example = "50000.0000") BigDecimal regulatoryMaxAmount,
        @Schema(description = "Share of the eligible maximum that is offered", example = "0.7000")
        BigDecimal recommendedRatio,
        @Schema(description = "Ceiling on total borrowing including debt held elsewhere; "
                + "null when the product sets none", example = "500000.0000")
        BigDecimal maxTotalExposure,

        List<ProductFeeResponse> fees,
        List<ProductRiskLimitResponse> riskLimits) {

    /** With everything: the terms, the fees and the per-grade ceilings. */
    public static ProductVersionResponse full(LoanProductVersion version) {
        return build(version,
                version.getFees().stream()
                        .sorted(Comparator.comparing(fee -> fee.getFeeCode()))
                        .map(ProductFeeResponse::from)
                        .toList(),
                version.getRiskLimits().stream()
                        .sorted(Comparator.comparing(limit -> limit.getRiskProfile()))
                        .map(ProductRiskLimitResponse::from)
                        .toList());
    }

    private static ProductVersionResponse build(LoanProductVersion version,
                                                List<ProductFeeResponse> fees,
                                                List<ProductRiskLimitResponse> riskLimits) {
        return new ProductVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getStatus().name(),
                version.getEffectiveFrom(),
                version.getEffectiveTo(),
                version.getCustomerSegment(),
                version.isSecured(),
                version.getCurrency(),
                version.getMinAmount(),
                version.getMaxAmount(),
                version.offeredTenures(),
                version.getInterestMethod().name(),
                version.getInterestRate(),
                version.getRepaymentFrequency().name(),
                version.getGracePeriodDays(),
                version.isCollateralRequired(),
                version.isGuarantorRequired(),
                version.getIncomeMultiple(),
                version.getMaxDbr(),
                version.getRegulatoryMaxAmount(),
                version.getRecommendedRatio(),
                version.getMaxTotalExposure(),
                fees,
                riskLimits);
    }
}
