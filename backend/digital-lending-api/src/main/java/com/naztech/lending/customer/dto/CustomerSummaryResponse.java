package com.naztech.lending.customer.dto;

import com.naztech.lending.customer.domain.Customer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A customer as a queue or search result shows them.
 *
 * <p>Carries no identification numbers and no financial detail. A list is
 * browsed far more often than a record is opened, so the shape that gets read
 * most carries the least.
 */
@Schema(description = "A customer, as listed")
public record CustomerSummaryResponse(
        @Schema(example = "CIF-000001") String customerId,
        String fullName,
        @Schema(example = "INDIVIDUAL") String customerType,
        @Schema(example = "01712345678") String mobile,
        @Schema(description = "Code of the branch holding the relationship", example = "BR-101")
        String branchCode,
        String branchName,
        @Schema(example = "MEDIUM") String riskProfile,
        @Schema(example = "VERIFIED") String kycStatus,
        String status) {

    public static CustomerSummaryResponse from(Customer customer) {
        return new CustomerSummaryResponse(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getCustomerType().name(),
                customer.getMobile(),
                customer.getHomeBranch() == null ? null : customer.getHomeBranch().getCode(),
                customer.getHomeBranch() == null ? null : customer.getHomeBranch().getName(),
                customer.getRiskProfile().name(),
                customer.getKycStatus().name(),
                customer.getStatus().name());
    }
}
