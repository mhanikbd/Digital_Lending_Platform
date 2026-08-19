package com.naztech.lending.rules.dto;

import com.naztech.lending.rules.domain.RuleGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A rule group as it is configured, rather than as it evaluated.
 *
 * <p>Every rule is spelt out in words. An administrator reviewing criteria they
 * did not write should not have to assemble an operator and two text columns in
 * their head, and a phrasing built here is the same phrasing that appears in the
 * decline the customer receives.
 */
@Schema(description = "A configured rule group")
public record RuleGroupConfigResponse(
        UUID id,
        @Schema(example = "ELOAN_V1_BASE") String code,
        @Schema(example = "e-Loan basic eligibility") String name,
        String description,
        @Schema(example = "ELIGIBILITY") String purpose,
        @Schema(example = "AND") String logic,
        @Schema(example = "10") int priority,
        @Schema(example = "ACTIVE") String status,
        @Schema(description = "The product version this applies to, or null for bank-wide")
        UUID productVersionId,
        String failureMessage,
        List<ConfiguredRule> rules) {

    public static RuleGroupConfigResponse from(RuleGroup group) {
        return new RuleGroupConfigResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                group.getDescription(),
                group.getPurpose().name(),
                group.getLogicalOperator().name(),
                group.getPriority(),
                group.getStatus().name(),
                group.getProductVersion() == null ? null : group.getProductVersion().getId(),
                group.getFailureMessage(),
                group.getRules().stream()
                        .sorted(Comparator.comparing(com.naztech.lending.rules.domain.Rule::getPriority))
                        .map(ConfiguredRule::from)
                        .toList());
    }

    /** One rule, as configured. */
    @Schema(description = "A configured rule")
    public record ConfiguredRule(
            UUID id,
            @Schema(example = "customer.age") String attribute,
            @Schema(example = "BETWEEN") String operator,
            @Schema(example = "21") String value,
            @Schema(description = "The upper bound, for BETWEEN only", example = "60")
            String secondValue,
            boolean negate,
            @Schema(example = "10") int priority,
            @Schema(example = "ACTIVE") String status,
            @Schema(example = "Age in years is between 21 and 60") String reads,
            String failureMessage) {

        static ConfiguredRule from(com.naztech.lending.rules.domain.Rule rule) {
            return new ConfiguredRule(
                    rule.getId(),
                    rule.getAttribute().getCode(),
                    rule.getOperator().name(),
                    rule.getComparisonValue(),
                    rule.getComparisonValue2(),
                    rule.isNegate(),
                    rule.getPriority(),
                    rule.getStatus().name(),
                    rule.describe(),
                    rule.getFailureMessage());
        }
    }
}
