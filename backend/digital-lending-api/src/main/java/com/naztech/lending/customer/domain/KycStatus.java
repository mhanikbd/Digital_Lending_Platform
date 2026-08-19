package com.naztech.lending.customer.domain;

/**
 * How far the customer's identity has been proven.
 *
 * <p>Written by the KYC module from Milestone 10 and read by the eligibility
 * rules, which is why it lives on the customer rather than inside a provider
 * response: a rule that has to call a vendor to find out whether a customer is
 * verified is a rule that cannot be evaluated offline.
 */
public enum KycStatus {
    PENDING,
    IN_PROGRESS,
    VERIFIED,
    REJECTED;

    public boolean isVerified() {
        return this == VERIFIED;
    }
}
