package com.naztech.lending.product.domain;

/**
 * When a fee is taken.
 *
 * <p>It changes what the borrower receives. A fee taken at disbursement is
 * deducted from the principal, so a customer approved for fifty thousand does
 * not receive fifty thousand - and the calculator has to say so plainly.
 */
public enum FeeCollectionPoint {

    /** Deducted from the amount paid out. */
    DISBURSEMENT,

    /** Added to each instalment. */
    EMI,

    /** Charged only if the borrower defaults, so never part of a quote. */
    ON_DEFAULT
}
