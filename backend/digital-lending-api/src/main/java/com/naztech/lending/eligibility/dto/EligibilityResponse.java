package com.naztech.lending.eligibility.dto;

import com.naztech.lending.rules.dto.RuleGroupResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the customer qualifies for.
 *
 * <p>Two halves that are often confused. Whether they may borrow at all is the
 * rule engine's answer, and it is a yes or a no with reasons. How much is the
 * amount engine's answer, and it exists only if the first was yes - quoting a
 * limit to somebody who has been declined would be read as an offer.
 *
 * <p>Every decimal is a JSON string. Parse with a decimal library.
 *
 * @param evaluationId the audit record this assessment was written to, so the
 *                     same answer can be produced again years later
 */
@Schema(description = "The outcome of an eligibility assessment")
public record EligibilityResponse(

        boolean eligible,

        @Schema(example = "CUS-100001") String customerId,
        @Schema(example = "ELOAN") String productCode,
        @Schema(example = "e-Loan") String productName,
        @Schema(description = "The product version assessed against", example = "1")
        int productVersion,
        @Schema(example = "BDT") String currency,

        @Schema(description = "The most that may be borrowed; null when not eligible",
                example = "30000.00") BigDecimal maxAmount,
        @Schema(description = "What the bank offers; null when not eligible",
                example = "21000.00") BigDecimal recommendedAmount,
        @Schema(description = "Tenures the product offers, in months", example = "[3, 6, 9, 12]")
        List<Integer> availableTenures,
        @Schema(description = "Percent per annum", example = "9.000000") BigDecimal interestRate,
        @Schema(example = "REDUCING_BALANCE") String interestMethod,

        @Schema(description = "The customer's risk grade as it stands today", example = "MEDIUM")
        String riskGrade,

        @Schema(description = "What to tell the customer when they are declined")
        List<String> reasons,

        @Schema(description = "Every criterion and its result, for a banker")
        List<RuleGroupResult> criteria,

        @Schema(description = "Why the amount is what it is; null when not eligible")
        AmountDecision limits,

        UUID evaluationId) {
}
