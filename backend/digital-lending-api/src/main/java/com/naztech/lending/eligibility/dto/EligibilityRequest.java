package com.naztech.lending.eligibility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Who to assess, and against what.
 *
 * <p>No amount, no rate, no tenure: this endpoint answers what the customer
 * qualifies for, not whether a figure somebody already chose is acceptable. The
 * caller supplies the two facts that cannot be derived - which customer, which
 * product - and the backend supplies everything else.
 */
@Schema(description = "A request to assess one customer against one product")
public record EligibilityRequest(

        @Schema(description = "The bank's customer identifier", example = "CUS-100001")
        @NotBlank(message = "A customer is required")
        String customerId,

        @Schema(description = "Product code, as published in the catalogue", example = "ELOAN")
        @NotBlank(message = "A product code is required")
        String productCode) {
}
