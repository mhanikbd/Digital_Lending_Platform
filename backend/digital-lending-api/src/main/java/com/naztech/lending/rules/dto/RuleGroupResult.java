package com.naztech.lending.rules.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One group of criteria and whether the subject satisfied it.
 *
 * <p>The group's own message is what a customer is told; the lines beneath it
 * are what a banker needs to answer "which one, exactly".
 */
@Schema(description = "A group of criteria and its result")
public record RuleGroupResult(
        @Schema(example = "ELOAN_V1_BASE") String code,
        @Schema(example = "e-Loan basic eligibility") String name,
        @Schema(example = "AND") String logic,
        boolean passed,
        @Schema(example = "You do not currently meet the basic eligibility criteria for this product.")
        String message,
        List<RuleLineResult> criteria) {
}
