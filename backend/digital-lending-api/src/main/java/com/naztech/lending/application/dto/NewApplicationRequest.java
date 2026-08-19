package com.naztech.lending.application.dto;

import com.naztech.lending.application.domain.SourceChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What is needed to raise a loan application.
 *
 * <p>No rate, no instalment, no total. The quotation is produced by the backend
 * from the live product version at the moment the file is raised - a client that
 * could supply it could raise a file at a rate the bank never offered.
 *
 * <p>The financial figures are optional and override what the customer record
 * already holds. An applicant who has had a pay rise since they were onboarded
 * should be assessed on the new figure, and the record of what they declared
 * belongs on the application rather than overwriting the customer master.
 */
@Schema(description = "A new loan application")
public record NewApplicationRequest(

        @Schema(description = "The bank's customer identifier", example = "CIF-000001")
        @NotBlank(message = "A customer is required")
        String customerId,

        @Schema(description = "Product code, as published in the catalogue", example = "ELOAN")
        @NotBlank(message = "A product code is required")
        String productCode,

        @Schema(description = "Amount to borrow", example = "35000")
        @NotNull(message = "An amount is required")
        @DecimalMin(value = "0.01", message = "The amount must be greater than zero")
        BigDecimal amount,

        @Schema(description = "Term in months; must be one the product offers", example = "12")
        @NotNull(message = "A tenure is required")
        @Min(value = 1, message = "The tenure must be at least one month")
        @Max(value = 480, message = "The tenure is longer than any product offers")
        Integer tenureMonths,

        @Schema(description = "Why the money is wanted", example = "MEDICAL")
        @NotBlank(message = "A purpose is required")
        String purposeCode,

        @Schema(description = "Required when the purpose asks for detail")
        @Size(max = 500, message = "The purpose detail may be at most 500 characters")
        String purposeDetail,

        @Schema(description = "How the application reached the bank; defaults to BRANCH")
        SourceChannel sourceChannel,

        @Schema(description = "Where the money goes", example = "1234567890123")
        @Size(max = 34, message = "An account number may be at most 34 characters")
        String disbursementAccount,

        @Schema(description = "The eligibility run this application follows from")
        UUID eligibilityId,

        @Schema(description = "Overrides the income on the customer record", example = "120000")
        @DecimalMin(value = "0.0", message = "Income cannot be negative")
        BigDecimal monthlyIncome,

        @Schema(example = "15000")
        @DecimalMin(value = "0.0", message = "Income cannot be negative")
        BigDecimal otherMonthlyIncome,

        @Schema(description = "Declared monthly outgoings", example = "40000")
        @DecimalMin(value = "0.0", message = "Expenses cannot be negative")
        BigDecimal monthlyExpense,

        @Schema(description = "Total borrowing held elsewhere", example = "850000")
        @DecimalMin(value = "0.0", message = "Liabilities cannot be negative")
        BigDecimal existingLiabilities,

        @Schema(description = "What that borrowing costs each month, which is what the "
                + "debt burden ratio actually needs", example = "18000")
        @DecimalMin(value = "0.0", message = "An instalment cannot be negative")
        BigDecimal existingEmi) {
}
