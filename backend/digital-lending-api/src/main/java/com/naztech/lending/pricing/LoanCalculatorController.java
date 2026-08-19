package com.naztech.lending.pricing;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.pricing.dto.LoanQuote;
import com.naztech.lending.pricing.dto.LoanQuoteRequest;
import com.naztech.lending.pricing.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The loan calculator of §20.
 *
 * <p>The backend's answer is the authoritative one. A mobile app may show an
 * indicative figure while the customer drags a slider, but what they are held to
 * is what this endpoint returned - which is why the rate comes from the product
 * and not from the request.
 *
 * <p>A negotiated rate is the single exception, and it needs {@code product.price}.
 * The permission is read here rather than enforced with {@code @PreAuthorize},
 * because the endpoint is open to everyone who may see a product: the check is
 * on one field of the request, not on the request.
 */
@RestController
@RequestMapping("/api/v1/loan-calculator")
@Tag(name = "Loan calculator", description = "Authoritative EMI, interest, fees and schedule")
public class LoanCalculatorController {

    private final PricingService pricing;

    public LoanCalculatorController(PricingService pricing) {
        this.pricing = pricing;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product.view')")
    @Operation(summary = "Quote a loan",
            description = "Returns the instalment, the interest, every fee with its VAT shown "
                    + "separately, the total payable, the net disbursement and the full repayment "
                    + "schedule. The rate is taken from the live product version; supplying "
                    + "rateOverride requires the product.price permission.")
    public ApiResponse<LoanQuote> quote(Authentication caller,
                                        @Valid @RequestBody LoanQuoteRequest request) {
        return ApiResponse.success(pricing.quote(request, mayNegotiate(caller)));
    }

    private boolean mayNegotiate(Authentication caller) {
        return caller != null && caller.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("product.price"::equals);
    }
}
