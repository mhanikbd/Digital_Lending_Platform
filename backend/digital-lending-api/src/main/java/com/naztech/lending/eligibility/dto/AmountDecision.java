package com.naztech.lending.eligibility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * How much the customer may borrow, and why that much.
 *
 * @param maxAmount         the lowest of every cap that applied
 * @param recommendedAmount what the bank offers, which is a configured share of
 *                          the maximum rather than the maximum itself
 * @param bindingFactor     the cap that decided it, so "why not more" has a
 *                          one-word answer before anybody reads the list
 * @param belowMinimum      true when even the maximum falls under what the
 *                          product will lend, which means no loan at all rather
 *                          than a small one
 */
@Schema(description = "The sized loan amount and the limits behind it")
public record AmountDecision(
        @Schema(example = "30000.00") BigDecimal maxAmount,
        @Schema(example = "21000.00") BigDecimal recommendedAmount,
        @Schema(example = "EXISTING_EXPOSURE") String bindingFactor,
        boolean belowMinimum,
        List<LimitFactor> factors) {
}
