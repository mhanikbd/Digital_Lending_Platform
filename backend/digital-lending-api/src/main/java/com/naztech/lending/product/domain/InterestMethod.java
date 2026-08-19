package com.naztech.lending.product.domain;

/**
 * How interest is charged.
 *
 * <p>The distinction is not cosmetic. At the same headline rate a flat loan
 * costs roughly twice a reducing-balance one over a year, because flat charges
 * the whole principal for the whole term regardless of what has been repaid.
 * Which method applies is therefore product configuration, never an assumption
 * baked into the calculator.
 */
public enum InterestMethod {

    /** Interest on the original principal for the full term. */
    FLAT,

    /** Interest on what is still owed, recalculated every period. */
    REDUCING_BALANCE,

    /**
     * Reducing balance, quoted as the effective annual rate the borrower
     * actually pays once compounding is taken into account.
     */
    EFFECTIVE;

    /** True when the schedule must be built period by period off the balance. */
    public boolean amortises() {
        return this != FLAT;
    }
}
