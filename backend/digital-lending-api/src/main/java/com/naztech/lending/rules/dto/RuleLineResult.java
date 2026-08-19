package com.naztech.lending.rules.dto;

import com.naztech.lending.rules.domain.Rule;
import com.naztech.lending.rules.service.RuleVerdict;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One rule and what it said, as it is shown to a banker.
 *
 * <p>The criterion is spelt out in words rather than returned as an operator and
 * a value for the client to phrase, because a client that phrases it is a client
 * that can phrase it differently from the audit record.
 */
@Schema(description = "One eligibility criterion and its result")
public record RuleLineResult(
        @Schema(example = "customer.age") String attribute,
        @Schema(example = "Age in years") String attributeName,
        @Schema(example = "Age in years is between 21 and 60") String criterion,
        @Schema(example = "34") String actualValue,
        boolean passed,
        @Schema(example = "Applicants must be between 21 and 60 years old.") String message) {

    public static RuleLineResult from(Rule rule, RuleVerdict verdict) {
        return new RuleLineResult(
                rule.getAttribute().getCode(),
                rule.getAttribute().getName(),
                rule.describe(),
                verdict.actualValue(),
                verdict.passed(),
                verdict.message());
    }
}
