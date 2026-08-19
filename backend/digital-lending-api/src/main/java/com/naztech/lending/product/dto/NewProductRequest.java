package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.InterestMethod;
import com.naztech.lending.product.domain.RepaymentFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A product and the first version of its terms.
 *
 * <p>Both at once, because a product with no version is not sellable and there
 * is no reason to create one in that state on purpose. The version arrives as a
 * draft all the same: registering a product and putting it on sale remain two
 * decisions.
 *
 * <p>Only the terms without which a loan cannot be quoted are required. A
 * product that caps nothing but its own maximum is a perfectly ordinary product,
 * so the limit parameters are optional and an absent one means "does not cap
 * that way" rather than "caps at zero".
 */
@Schema(description = "A new product, with the first draft of its terms")
public record NewProductRequest(

        @Schema(example = "QUICKLOAN")
        @NotBlank(message = "A product code is required")
        @Size(max = 30, message = "A product code may be at most 30 characters")
        @Pattern(regexp = "^[A-Z0-9_]+$",
                message = "A product code may contain only capital letters, digits and underscores")
        String code,

        @Schema(example = "Quick Loan")
        @NotBlank(message = "A product name is required")
        @Size(max = 120, message = "A product name may be at most 120 characters")
        String name,

        @Schema(description = "The name in Bangla, for customer-facing surfaces")
        @Size(max = 160, message = "The Bangla name may be at most 160 characters")
        String nameBn,

        @Schema(example = "TERM_LOAN", allowableValues = {"TERM_LOAN", "CREDIT_CARD", "OVERDRAFT"})
        @NotBlank(message = "A product type is required")
        String productType,

        @Schema(example = "PERSONAL",
                allowableValues = {"PERSONAL", "SME", "HOME", "AUTO", "STUDENT", "CARD"})
        @NotBlank(message = "A category is required")
        String category,

        @Size(max = 500, message = "A description may be at most 500 characters")
        String description,

        @Schema(description = "Defaults to today") LocalDate effectiveFrom,

        @Schema(example = "BDT")
        @Pattern(regexp = "^[A-Z]{3}$", message = "A currency is a three letter ISO code")
        String currency,

        @Schema(example = "5000.0000")
        @NotNull(message = "A minimum amount is required")
        @DecimalMin(value = "0.0001", message = "The minimum amount must be greater than zero")
        BigDecimal minAmount,

        @Schema(example = "50000.0000")
        @NotNull(message = "A maximum amount is required")
        @DecimalMin(value = "0.0001", message = "The maximum amount must be greater than zero")
        BigDecimal maxAmount,

        @Schema(description = "The tenures offered, in months", example = "[3, 6, 9, 12]")
        @NotEmpty(message = "A product must offer at least one tenure")
        List<Integer> tenures,

        @Schema(example = "REDUCING_BALANCE")
        @NotNull(message = "An interest method is required")
        InterestMethod interestMethod,

        @Schema(description = "Percent per annum", example = "9.000000")
        @NotNull(message = "An interest rate is required")
        @DecimalMin(value = "0.0", message = "A rate cannot be negative")
        BigDecimal interestRate,

        @Schema(description = "Defaults to MONTHLY") RepaymentFrequency repaymentFrequency,

        @Schema(description = "Months of income the product will lend", example = "10.0000")
        @DecimalMin(value = "0.0", message = "An income multiple cannot be negative")
        BigDecimal incomeMultiple,

        @Schema(description = "Fraction of income, so 0.5000 is fifty percent", example = "0.5000")
        @DecimalMin(value = "0.0001", message = "The debt burden ratio must be greater than zero")
        @DecimalMax(value = "1.0", message = "The debt burden ratio cannot exceed the whole income")
        BigDecimal maxDbr,

        @Schema(example = "50000.0000")
        @DecimalMin(value = "0.0", message = "A regulatory ceiling cannot be negative")
        BigDecimal regulatoryMaxAmount,

        @Schema(description = "Share of the eligible maximum that is offered; defaults to 0.7000",
                example = "0.7000")
        @DecimalMin(value = "0.0001", message = "The recommended share must be greater than zero")
        @DecimalMax(value = "1.0", message = "The recommended share cannot exceed the maximum")
        BigDecimal recommendedRatio,

        @Schema(description = "Ceiling on total borrowing including debt held elsewhere. "
                + "Leave unset unless the product genuinely caps concentration - affordability "
                + "is what maxDbr measures.", example = "500000.0000")
        @DecimalMin(value = "0.0", message = "An exposure ceiling cannot be negative")
        BigDecimal maxTotalExposure) {
}
