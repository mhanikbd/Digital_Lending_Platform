package com.naztech.lending.rules.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What a whole rule run decided.
 *
 * <p>{@code reasons} is the short answer - the messages a customer would be
 * shown, in the order the groups ran. {@code groups} is the long one, for a
 * banker who has to explain the short one.
 *
 * @param evaluationId the audit record this run was written to, so a decision
 *                     can be produced again years later without recomputing it
 */
@Schema(description = "The result of running a set of rule groups")
public record RuleRunResult(
        boolean passed,
        List<String> reasons,
        List<RuleGroupResult> groups,
        UUID evaluationId) {

    /** True when no group applied at all, which is not the same as passing. */
    public boolean evaluatedNothing() {
        return groups.isEmpty();
    }
}
