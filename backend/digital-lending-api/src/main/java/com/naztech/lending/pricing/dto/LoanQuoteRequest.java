package com.naztech.lending.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * What to quote.
 *
 * <p>The product is named by its code and the terms are looked up; a caller
 * cannot supply the rate. That is the point of §20 - a client may show an
 * indicative figure, but the backend's answer is the one that counts, and it
 * cannot be if the client gets to choose the inputs to it.
 *
 * <p>The one exception is {@code rateOverride}, which needs a permission the
 * public calculator does not grant. It exists for a banker negotiating a
 * promotional rate, and it is recorded in the quote so the figure can never be
 * mistaken for the standard one.
 */
@Schema(description = "A request for an authoritative loan quotation")
public record LoanQuoteRequest(

        @Schema(description = "Product code, as published in the catalogue", example = "ELOAN")
        @NotBlank(message = "A product code is required")
        String productCode,

        @Schema(description = "Amount to borrow, in the product's currency", example = "35000")
        @NotNull(message = "An amount is required")
        @DecimalMin(value = "0.01", message = "The amount must be greater than zero")
        BigDecimal amount,

        @Schema(description = "Term in months; must be one the product offers", example = "12")
        @NotNull(message = "A tenure is required")
        @Min(value = 1, message = "The tenure must be at least one month")
        @Max(value = 480, message = "The tenure is longer than any product offers")
        Integer tenureMonths,

        @Schema(description = "A negotiated rate, percent per annum. Requires product.price",
                example = "8.5")
        @DecimalMin(value = "0.0", message = "A rate cannot be negative")
        BigDecimal rateOverride) {
}
