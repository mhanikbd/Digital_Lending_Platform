package com.naztech.lending.product.domain;

/** How often an instalment falls due. */
public enum RepaymentFrequency {

    MONTHLY(12),
    QUARTERLY(4),
    HALF_YEARLY(2),
    YEARLY(1);

    private final int periodsPerYear;

    RepaymentFrequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    /** Divides an annual rate into a periodic one, and months into instalments. */
    public int periodsPerYear() {
        return periodsPerYear;
    }

    /** Months covered by one instalment. */
    public int monthsPerPeriod() {
        return 12 / periodsPerYear;
    }
}
