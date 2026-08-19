package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.ProductFee;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One configured fee, as it is set rather than as it is charged. */
@Schema(description = "A fee configured on a product version")
public record ProductFeeResponse(
        @Schema(example = "PROCESSING") String code,
        @Schema(example = "Processing fee") String name,
        @Schema(example = "PERCENT_OF_PRINCIPAL") String calculationMethod,
        @Schema(description = "Set for a flat fee, null for a percentage") BigDecimal flatAmount,
        @Schema(description = "Percent of principal, null for a flat fee", example = "1.000000")
        BigDecimal rate,
        @Schema(description = "Percent, charged on the fee and not on the loan",
                example = "15.000000") BigDecimal vatRate,
        @Schema(example = "DISBURSEMENT") String collectedAt,
        boolean mandatory) {

    public static ProductFeeResponse from(ProductFee fee) {
        return new ProductFeeResponse(
                fee.getFeeCode(),
                fee.getName(),
                fee.getCalculationMethod().name(),
                fee.getFlatAmount(),
                fee.getRate(),
                fee.getVatRate(),
                fee.getCollectedAt().name(),
                fee.isMandatory());
    }
}
