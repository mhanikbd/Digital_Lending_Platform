package com.naztech.lending.customer.dto;

import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.domain.CustomerAddress;
import com.naztech.lending.customer.domain.CustomerIdentification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The whole customer record.
 *
 * <p>This shape carries personal data: parentage, date of birth, income and
 * document numbers. It is returned only to a caller holding {@code
 * customer.view} and only for a customer inside their organisational scope, and
 * it is the reason that permission is not granted casually.
 *
 * <p>Every monetary value is a {@link BigDecimal}, which the platform serialises
 * as a plain JSON string rather than a number, so a JavaScript client cannot
 * quietly round someone's income into a 64-bit float.
 */
@Schema(description = "A customer in full")
public record CustomerDetailResponse(
        @Schema(example = "CIF-000001") String customerId,
        @Schema(example = "INDIVIDUAL") String customerType,
        String status,

        String fullName,
        String fatherName,
        String motherName,
        String spouseName,
        LocalDate dateOfBirth,
        @Schema(description = "Derived from the date of birth, so it is right the day after a birthday")
        Integer age,
        String gender,
        String nationality,
        String maritalStatus,
        String educationLevel,
        String residenceStatus,

        String mobile,
        String email,

        String occupation,
        String designation,
        String employerName,

        @Schema(description = "Serialised as a string, never a JSON number", example = "85000.0000")
        BigDecimal monthlyIncome,
        BigDecimal otherMonthlyIncome,
        @Schema(description = "Primary plus other, so a caller need not add two optional values")
        BigDecimal totalMonthlyIncome,
        String sourceOfIncome,
        String sourceOfFunds,
        BigDecimal netWorth,
        BigDecimal existingLiabilities,
        @Schema(example = "BDT") String currency,

        String riskProfile,
        String kycStatus,
        LocalDate onboardedOn,
        @Schema(description = "Whether the platform will consider this customer for lending")
        boolean eligibleToBorrow,

        String branchCode,
        String branchName,

        List<AddressResponse> addresses,
        List<IdentificationResponse> identifications) {

    @Schema(description = "One address")
    public record AddressResponse(
            @Schema(example = "PRESENT") String addressType,
            String line1,
            String city,
            String district,
            String postalCode,
            String country,
            @Schema(description = "The same address on one line, as a letter would carry it")
            String formatted) {
    }

    @Schema(description = "One identification document")
    public record IdentificationResponse(
            @Schema(example = "NID") String idType,
            String idNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuePlace,
            @Schema(description = "True only once an authority has confirmed it, never because "
                    + "the customer typed it")
            boolean verified,
            boolean expired) {
    }

    public static CustomerDetailResponse from(Customer customer, LocalDate today) {
        return new CustomerDetailResponse(
                customer.getCustomerId(),
                customer.getCustomerType().name(),
                customer.getStatus().name(),
                customer.getFullName(),
                customer.getFatherName(),
                customer.getMotherName(),
                customer.getSpouseName(),
                customer.getDateOfBirth(),
                customer.ageOn(today).orElse(null),
                customer.getGender() == null ? null : customer.getGender().name(),
                customer.getNationality(),
                customer.getMaritalStatus() == null ? null : customer.getMaritalStatus().name(),
                customer.getEducationLevel(),
                customer.getResidenceStatus().name(),
                customer.getMobile(),
                customer.getEmail(),
                customer.getOccupation(),
                customer.getDesignation(),
                customer.getEmployerName(),
                customer.getMonthlyIncome(),
                customer.getOtherMonthlyIncome(),
                customer.totalMonthlyIncome(),
                customer.getSourceOfIncome(),
                customer.getSourceOfFunds(),
                customer.getNetWorth(),
                customer.getExistingLiabilities(),
                customer.getCurrency(),
                customer.getRiskProfile().name(),
                customer.getKycStatus().name(),
                customer.getOnboardedOn(),
                customer.isEligibleToBorrow(),
                customer.getHomeBranch() == null ? null : customer.getHomeBranch().getCode(),
                customer.getHomeBranch() == null ? null : customer.getHomeBranch().getName(),
                customer.getAddresses().stream()
                        .sorted(Comparator.comparing(address -> address.getAddressType().name()))
                        .map(CustomerDetailResponse::address)
                        .toList(),
                customer.getIdentifications().stream()
                        .sorted(Comparator.comparing(document -> document.getIdType().name()))
                        .map(document -> identification(document, today))
                        .toList());
    }

    private static AddressResponse address(CustomerAddress address) {
        return new AddressResponse(
                address.getAddressType().name(),
                address.getLine1(),
                address.getCity(),
                address.getDistrict(),
                address.getPostalCode(),
                address.getCountry(),
                address.formatted());
    }

    private static IdentificationResponse identification(CustomerIdentification document,
                                                         LocalDate today) {
        return new IdentificationResponse(
                document.getIdType().name(),
                document.getIdNumber(),
                document.getIssueDate(),
                document.getExpiryDate(),
                document.getIssuePlace(),
                document.isVerified(),
                document.isExpiredOn(today));
    }
}
