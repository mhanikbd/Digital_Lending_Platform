package com.naztech.lending.application.dto;

import com.naztech.lending.application.domain.LoanApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An application as it appears in a queue.
 *
 * <p>Carries the workflow step as well as the state, because a queue groups by
 * step and a screen that had to work out which step {@code CA_RETURNED} belongs
 * to would eventually get it wrong - it is an origination state despite its
 * name.
 */
@Schema(description = "A loan application, as a queue shows it")
public record ApplicationSummaryResponse(
        @Schema(example = "APP-2026-000042") String applicationNo,
        @Schema(example = "CIF-000001") String customerId,
        @Schema(example = "Rahim Uddin Ahmed") String customerName,
        @Schema(example = "ELOAN") String productCode,
        @Schema(example = "e-Loan") String productName,
        @Schema(example = "35000.0000") BigDecimal requestedAmount,
        @Schema(description = "What was approved, when somebody has approved something")
        BigDecimal approvedAmount,
        @Schema(example = "12") int tenureMonths,
        @Schema(example = "SO_CREATED") String stateCode,
        @Schema(example = "With the sourcing officer") String stateName,
        @Schema(example = "1") int stepNo,
        @Schema(example = "Origination") String stepName,
        @Schema(description = "What the customer is told", example = "IN_PROGRESS")
        String customerStage,
        @Schema(example = "BR-101") String branchCode,
        @Schema(example = "FIELD_OFFICER") String sourceChannel,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationSummaryResponse from(LoanApplication application) {
        return new ApplicationSummaryResponse(
                application.getApplicationNo(),
                application.getCustomer().getCustomerId(),
                application.getCustomer().getFullName(),
                application.getProduct().getCode(),
                application.getProduct().getName(),
                application.getRequestedAmount(),
                application.getApprovedAmount(),
                application.getRequestedTenureMonths(),
                application.getState().getCode(),
                application.getState().getName(),
                application.getState().getStepNo(),
                application.getState().getStepName(),
                application.getState().getCustomerStage().name(),
                application.getBranch() == null ? null : application.getBranch().getCode(),
                application.getSourceChannel().name(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
