package com.naztech.lending.pricing.dto;

import com.naztech.lending.product.domain.ProductFee;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One charge, itemised.
 *
 * <p>Base and VAT are separate because they are separate in law: the VAT is
 * collected on the bank's behalf and remitted, and a customer disputing a charge
 * is entitled to see which part is which.
 */
@Schema(description = "One fee, with its VAT shown separately")
public record FeeLine(
        @Schema(example = "PROCESSING") String code,
        @Schema(example = "Processing fee") String name,
        @Schema(example = "PERCENT_OF_PRINCIPAL") String calculationMethod,
        @Schema(description = "Percent of principal, or null for a flat fee", example = "1.000000")
        BigDecimal rate,
        @Schema(example = "350.00") BigDecimal amount,
        @Schema(example = "15.000000") BigDecimal vatRate,
        @Schema(example = "52.50") BigDecimal vat,
        @Schema(example = "402.50") BigDecimal total,
        @Schema(example = "DISBURSEMENT") String collectedAt) {

    public static FeeLine of(ProductFee fee, BigDecimal principal) {
        return new FeeLine(
                fee.getFeeCode(),
                fee.getName(),
                fee.getCalculationMethod().name(),
                fee.getRate(),
                fee.baseAmountOn(principal),
                fee.getVatRate(),
                fee.vatOn(principal),
                fee.totalOn(principal),
                fee.getCollectedAt().name());
    }
}
