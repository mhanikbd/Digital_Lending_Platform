package com.naztech.lending.customer.domain;

/**
 * Whether the customer resides in Bangladesh.
 *
 * <p>Not cosmetic: a non-resident is a different proposition for eligibility,
 * for the documents required, and for the regulator.
 */
public enum ResidenceStatus {
    RESIDENT,
    NON_RESIDENT
}
