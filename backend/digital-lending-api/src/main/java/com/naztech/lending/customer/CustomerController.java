package com.naztech.lending.customer;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.customer.dto.CustomerDetailResponse;
import com.naztech.lending.customer.dto.CustomerSummaryResponse;
import com.naztech.lending.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer master.
 *
 * <p>Two gates, not one. The permission decides whether a caller may read
 * customers at all; their organisational scope decides which. Holding
 * {@code customer.view} at a branch does not open the bank's whole book, and
 * that narrowing is applied in the service rather than trusted to the caller.
 *
 * <p>These responses carry personal data. Nothing here returns a credential,
 * and nothing here writes.
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "The customer master, within the reader's scope")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "List customers",
            description = "Every customer the caller is entitled to see. A branch-scoped reader "
                    + "gets the customers of the branches they are posted to; a head office "
                    + "reader is not narrowed. Requires the customer.view permission.")
    public ApiResponse<List<CustomerSummaryResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(customers.list(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "One customer in full",
            description = "Includes addresses, identification documents and the financial "
                    + "profile. A customer outside the caller's scope answers 404, exactly as one "
                    + "that does not exist, so the endpoint cannot be used to discover which "
                    + "branch holds a given customer.")
    public ApiResponse<CustomerDetailResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable String customerId) {
        return ApiResponse.success(
                customers.detail(UUID.fromString(jwt.getSubject()), customerId));
    }
}
