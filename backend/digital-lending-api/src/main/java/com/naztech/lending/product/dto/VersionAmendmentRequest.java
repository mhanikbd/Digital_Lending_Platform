package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.InterestMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What to change when drafting the next version.
 *
 * <p>Every field is optional and a null means "as it was". A repricing is
 * usually one number, and requiring the whole configuration to be resubmitted to
 * change one is how a rate gets reset to a stale value by accident.
 */
@Schema(description = "The amendments to apply when drafting a new product version")
public record VersionAmendmentRequest(

        @Schema(description = "The day the new version is intended to take effect. "
                + "Defaults to today; activation sets it again.")
        LocalDate effectiveFrom,

        @Schema(example = "5000.0000")
        @DecimalMin(value = "0.0001", message = "The minimum amount must be greater than zero")
        BigDecimal minAmount,

        @Schema(example = "75000.0000")
        @DecimalMin(value = "0.0001", message = "The maximum amount must be greater than zero")
        BigDecimal maxAmount,

        @Schema(example = "REDUCING_BALANCE") InterestMethod interestMethod,

        @Schema(description = "Percent per annum", example = "10.500000")
        @DecimalMin(value = "0.0", message = "A rate cannot be negative")
        BigDecimal interestRate,

        @Schema(example = "12.0000")
        @DecimalMin(value = "0.0", message = "An income multiple cannot be negative")
        BigDecimal incomeMultiple,

        @Schema(description = "Fraction of income, so 0.5000 is fifty percent", example = "0.4500")
        @DecimalMin(value = "0.0001", message = "The debt burden ratio must be greater than zero")
        @DecimalMax(value = "1.0", message = "The debt burden ratio cannot exceed the whole income")
        BigDecimal maxDbr,

        @Schema(example = "100000.0000")
        @DecimalMin(value = "0.0", message = "A regulatory ceiling cannot be negative")
        BigDecimal regulatoryMaxAmount,

        @Schema(description = "Share of the eligible maximum that is offered", example = "0.7000")
        @DecimalMin(value = "0.0001", message = "The recommended share must be greater than zero")
        @DecimalMax(value = "1.0", message = "The recommended share cannot exceed the maximum")
        BigDecimal recommendedRatio,

        @Schema(description = "Ceiling on total borrowing including debt held elsewhere",
                example = "500000.0000")
        @DecimalMin(value = "0.0", message = "An exposure ceiling cannot be negative")
        BigDecimal maxTotalExposure,

        @Schema(description = "The tenures to offer, in months. Replaces the existing list.",
                example = "[3, 6, 9, 12, 18]")
        List<Integer> tenures) {

    /** Nothing named, which is a straight copy of the current version. */
    public static VersionAmendmentRequest none() {
        return new VersionAmendmentRequest(null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
