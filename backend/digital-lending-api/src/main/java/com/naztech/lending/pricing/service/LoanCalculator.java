package com.naztech.lending.pricing.service;

import com.naztech.lending.product.domain.InterestMethod;
import com.naztech.lending.product.domain.RepaymentFrequency;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The arithmetic of a loan.
 *
 * <p>Pure: numbers in, numbers out. No Spring, no database, no clock, no
 * product entity. That is what lets §20's "unit tests for all calculation types
 * and rounding rules" actually be unit tests, and it is why the product lookup
 * lives in {@link PricingService} instead of here.
 *
 * <h2>Rounding</h2>
 * Two scales, and the distinction matters. Working values carry ten decimal
 * places so that compounding does not accumulate error; anything a customer is
 * shown, pays or owes is rounded to two, half-up. Rounding once at the end of
 * each figure - never repeatedly through the calculation - is what keeps the
 * schedule adding up to the total.
 *
 * <h2>The last instalment</h2>
 * A rounded EMI multiplied by the number of instalments almost never equals the
 * exact total. The difference, a few paisa, is put on the final instalment
 * rather than spread or ignored, which is what a loan system has to do: the
 * balance must reach exactly zero and the schedule must sum to exactly the
 * total payable.
 */
public final class LoanCalculator {

    /** What a customer sees and pays. */
    public static final int MONEY_SCALE = 2;

    /** What the arithmetic carries between steps. */
    private static final int WORKING_SCALE = 10;

    private static final MathContext WORKING = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE = BigDecimal.ONE;

    private LoanCalculator() {
    }

    /**
     * The instalment, the interest, and the schedule behind them.
     *
     * @param principal    the amount borrowed
     * @param tenureMonths the term, which must be a whole number of periods
     * @param annualRate   percent per annum as a product sheet states it, so 9
     *                     means nine percent
     */
    public static Amortisation amortise(BigDecimal principal, int tenureMonths,
                                        BigDecimal annualRate, InterestMethod method,
                                        RepaymentFrequency frequency) {
        require(principal != null && principal.signum() > 0, "Principal must be greater than zero");
        require(annualRate != null && annualRate.signum() >= 0, "Interest rate cannot be negative");
        require(tenureMonths > 0, "Tenure must be at least one month");
        require(tenureMonths % frequency.monthsPerPeriod() == 0,
                "Tenure must be a whole number of %s periods".formatted(frequency.name().toLowerCase()));

        int periods = tenureMonths / frequency.monthsPerPeriod();

        return method == InterestMethod.FLAT
                ? flat(principal, periods, tenureMonths, annualRate, frequency)
                : reducing(principal, periods, annualRate, method, frequency);
    }

    /**
     * Interest charged on the original principal for the whole term, regardless
     * of what has been repaid.
     *
     * <p>Every instalment carries the same interest, which is why a flat rate of
     * nine percent costs roughly what a reducing rate of sixteen would. The
     * method is configuration precisely so that difference is a decision.
     */
    private static Amortisation flat(BigDecimal principal, int periods, int tenureMonths,
                                     BigDecimal annualRate, RepaymentFrequency frequency) {
        BigDecimal years = BigDecimal.valueOf(tenureMonths)
                .divide(BigDecimal.valueOf(12), WORKING_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalInterest = principal
                .multiply(annualRate)
                .divide(HUNDRED, WORKING_SCALE, RoundingMode.HALF_UP)
                .multiply(years)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalPayable = principal.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                .add(totalInterest);
        BigDecimal instalment = totalPayable
                .divide(BigDecimal.valueOf(periods), MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal principalPerPeriod = principal.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(periods), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal interestPerPeriod = totalInterest
                .divide(BigDecimal.valueOf(periods), MONEY_SCALE, RoundingMode.HALF_UP);

        List<Instalment> schedule = new ArrayList<>(periods);
        BigDecimal balance = principal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (int number = 1; number <= periods; number++) {
            boolean last = number == periods;
            BigDecimal principalPart = last ? balance : principalPerPeriod;
            BigDecimal interestPart = last
                    ? totalInterest.subtract(interestPerPeriod.multiply(BigDecimal.valueOf(periods - 1L)))
                    : interestPerPeriod;
            balance = balance.subtract(principalPart);
            schedule.add(new Instalment(number, principalPart.add(interestPart),
                    principalPart, interestPart, balance.max(BigDecimal.ZERO)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
        }

        return new Amortisation(instalment, totalInterest, totalPayable,
                periodicRateOf(annualRate, InterestMethod.FLAT, frequency), periods, schedule);
    }

    /**
     * Interest charged on what is still owed.
     *
     * <p>The standard annuity: EMI = P·i / (1 − (1+i)^−n). A zero rate would
     * divide by zero, so it is handled as the straight-line repayment it is.
     */
    private static Amortisation reducing(BigDecimal principal, int periods, BigDecimal annualRate,
                                         InterestMethod method, RepaymentFrequency frequency) {
        BigDecimal rate = periodicRateOf(annualRate, method, frequency);
        BigDecimal openingBalance = principal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal instalment;
        if (rate.signum() == 0) {
            instalment = openingBalance
                    .divide(BigDecimal.valueOf(periods), MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            BigDecimal growth = ONE.add(rate).pow(periods, WORKING);
            instalment = openingBalance.multiply(rate).multiply(growth)
                    .divide(growth.subtract(ONE), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        // The schedule is built by walking the balance down rather than by
        // formula, because the balance is what the loan actually owes and the
        // formula is only a prediction of it.
        List<Instalment> schedule = new ArrayList<>(periods);
        BigDecimal balance = openingBalance;
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(MONEY_SCALE);

        for (int number = 1; number <= periods; number++) {
            BigDecimal interestPart = balance.multiply(rate)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal principalPart;
            BigDecimal due;

            if (number == periods) {
                // Whatever is left, so the balance lands on exactly zero.
                principalPart = balance;
                due = principalPart.add(interestPart);
            } else {
                principalPart = instalment.subtract(interestPart);
                if (principalPart.signum() <= 0) {
                    // The instalment does not even cover the interest, which
                    // means the loan would never amortise. Refuse rather than
                    // silently produce a schedule that grows.
                    throw new IllegalArgumentException(
                            "At this rate and tenure the instalment cannot repay the loan");
                }
                due = instalment;
            }

            balance = balance.subtract(principalPart);
            totalInterest = totalInterest.add(interestPart);
            schedule.add(new Instalment(number, due, principalPart, interestPart,
                    balance.setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
        }

        BigDecimal totalPayable = openingBalance.add(totalInterest);
        return new Amortisation(instalment, totalInterest, totalPayable, rate, periods, schedule);
    }

    /**
     * The rate applied to one period.
     *
     * <p>A nominal rate is divided by the number of periods in a year, which is
     * the convention every loan agreement in the market is written on. An
     * effective rate is the rate actually earned over a year once compounding is
     * counted, so it is un-compounded by taking the appropriate root.
     *
     * <p>The root is the one step taken in double precision - there is no exact
     * decimal n-th root - and it produces a rate, not money. It is immediately
     * fixed at ten decimal places, and every figure computed from it is
     * BigDecimal throughout.
     */
    public static BigDecimal periodicRateOf(BigDecimal annualRate, InterestMethod method,
                                            RepaymentFrequency frequency) {
        BigDecimal annualFraction = annualRate.divide(HUNDRED, WORKING_SCALE, RoundingMode.HALF_UP);
        int periodsPerYear = frequency.periodsPerYear();

        if (method != InterestMethod.EFFECTIVE) {
            return annualFraction.divide(
                    BigDecimal.valueOf(periodsPerYear), WORKING_SCALE, RoundingMode.HALF_UP);
        }
        double periodic = Math.pow(1 + annualFraction.doubleValue(), 1.0 / periodsPerYear) - 1;
        return BigDecimal.valueOf(periodic).setScale(WORKING_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The largest principal whose instalment stays within a ceiling.
     *
     * <p>The amortisation run backwards, and the reason the debt burden ratio
     * can be expressed as a limit on the amount rather than only as a test on a
     * proposed one. Rounded <em>down</em> to whole currency: rounding up would
     * produce an instalment a paisa over the ceiling the bank just set.
     */
    public static BigDecimal principalAffordableAt(BigDecimal maxInstalment, int tenureMonths,
                                                   BigDecimal annualRate, InterestMethod method,
                                                   RepaymentFrequency frequency) {
        require(maxInstalment != null, "An instalment ceiling is required");
        require(tenureMonths > 0, "Tenure must be at least one month");
        require(tenureMonths % frequency.monthsPerPeriod() == 0,
                "Tenure must be a whole number of periods");

        if (maxInstalment.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        int periods = tenureMonths / frequency.monthsPerPeriod();
        BigDecimal rate = periodicRateOf(annualRate, method, frequency);

        BigDecimal principal;
        if (method == InterestMethod.FLAT) {
            // instalment = (P + P·r·years) / n  ->  P = instalment·n / (1 + r·years)
            BigDecimal years = BigDecimal.valueOf(tenureMonths)
                    .divide(BigDecimal.valueOf(12), WORKING_SCALE, RoundingMode.HALF_UP);
            BigDecimal annualFraction = annualRate.divide(HUNDRED, WORKING_SCALE, RoundingMode.HALF_UP);
            BigDecimal factor = ONE.add(annualFraction.multiply(years));
            principal = maxInstalment.multiply(BigDecimal.valueOf(periods))
                    .divide(factor, WORKING_SCALE, RoundingMode.HALF_UP);
        } else if (rate.signum() == 0) {
            principal = maxInstalment.multiply(BigDecimal.valueOf(periods));
        } else {
            BigDecimal growth = ONE.add(rate).pow(periods, WORKING);
            BigDecimal annuityFactor = growth.subtract(ONE)
                    .divide(rate.multiply(growth), WORKING_SCALE, RoundingMode.HALF_UP);
            principal = maxInstalment.multiply(annuityFactor);
        }

        return principal.setScale(0, RoundingMode.DOWN).setScale(MONEY_SCALE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * One line of the repayment schedule.
     *
     * @param closingBalance what is still owed after this instalment, which
     *                       reaches exactly zero on the last one
     */
    public record Instalment(int number, BigDecimal amountDue, BigDecimal principal,
                             BigDecimal interest, BigDecimal closingBalance) {
    }

    /**
     * The interest side of a loan, before any fee is applied.
     *
     * @param periodicRate the rate actually used per period, exposed because a
     *                     banker asked "why is the interest that number" needs
     *                     it and recomputing it invites disagreement
     */
    public record Amortisation(BigDecimal instalment, BigDecimal totalInterest,
                               BigDecimal totalPayable, BigDecimal periodicRate,
                               int periods, List<Instalment> schedule) {
    }
}
