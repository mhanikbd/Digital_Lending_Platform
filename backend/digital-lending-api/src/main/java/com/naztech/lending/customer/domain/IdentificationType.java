package com.naztech.lending.customer.domain;

/** The documents a customer can prove themselves with. */
public enum IdentificationType {

    /** National ID. Unique across the whole customer master, by index. */
    NID,

    /** Taxpayer identification number. */
    TIN,

    PASSPORT,
    DRIVING_LICENCE,

    /** Accepted for a minor, who has no national ID yet. */
    BIRTH_CERTIFICATE
}
