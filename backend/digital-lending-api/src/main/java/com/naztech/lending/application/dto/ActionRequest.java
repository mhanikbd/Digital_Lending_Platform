package com.naztech.lending.application.dto;

import com.naztech.lending.application.domain.QueryType;
import com.naztech.lending.workflow.domain.WorkflowAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An action taken on an application.
 *
 * <p>The action is the only required field. Everything else is what a particular
 * action needs: a reason for the moves that demand one, an amount for an
 * approval that cuts the loan, a destination for the rare move that could land
 * in two places.
 *
 * <p>Whether the reason is required is not decided here. The transition says so,
 * because a bank that decides recommendations should carry a note changes a row
 * rather than this class.
 */
@Schema(description = "An action to take on a loan application")
public record ActionRequest(

        @Schema(description = "One of the actions available-actions offered", example = "RECOMMEND")
        @NotNull(message = "An action is required")
        WorkflowAction action,

        @Schema(description = "Required when the transition demands it - returns, "
                + "rejections and escalations always do. Carries the question when the "
                + "action is QUERY, and the answer when a query is being answered.")
        @Size(max = 1000, message = "A reason may be at most 1000 characters")
        String reason,

        @Schema(description = "Only needed when the action could land in more than one "
                + "state and the caller's role does not narrow it to one")
        @Size(max = 40) String toState,

        @Schema(description = "What the approver settled on. Absent means the amount was "
                + "taken as asked.", example = "30000")
        @DecimalMin(value = "0.01", message = "An approved amount must be greater than zero")
        BigDecimal approvedAmount,

        @Schema(example = "12")
        @Min(value = 1, message = "An approved tenure must be at least one month")
        Integer approvedTenureMonths,

        @Schema(description = "What kind of query is being raised") QueryType queryType,

        @Schema(description = "A note to attach alongside the action")
        @Size(max = 2000, message = "A comment may be at most 2000 characters")
        String comment) {
}
