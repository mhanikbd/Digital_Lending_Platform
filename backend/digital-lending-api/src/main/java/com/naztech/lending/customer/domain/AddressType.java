package com.naztech.lending.customer.domain;

/**
 * Present, permanent and business addresses.
 *
 * <p>Routinely different in Bangladesh: people bank where they work and are
 * registered where their family is, so a single address field would lose one of
 * the two the regulator asks for.
 */
public enum AddressType {
    PRESENT,
    PERMANENT,
    BUSINESS
}
