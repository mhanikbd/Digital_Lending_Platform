package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.ProductRiskLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** What one risk grade may borrow under a version. */
@Schema(description = "A per-grade lending ceiling")
public record ProductRiskLimitResponse(
        @Schema(example = "MEDIUM") String riskProfile,
        @Schema(example = "35000.0000") BigDecimal maxAmount) {

    public static ProductRiskLimitResponse from(ProductRiskLimit limit) {
        return new ProductRiskLimitResponse(limit.getRiskProfile(), limit.getMaxAmount());
    }
}
