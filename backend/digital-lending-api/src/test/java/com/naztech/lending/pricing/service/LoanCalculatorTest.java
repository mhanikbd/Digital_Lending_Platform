package com.naztech.lending.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naztech.lending.product.domain.InterestMethod;
import com.naztech.lending.product.domain.RepaymentFrequency;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The arithmetic §20 calls for: "unit tests for all calculation types and
 * rounding rules".
 *
 * <p>Two things are being protected. The first is that the numbers are right -
 * a flat rate and a reducing rate must not be interchangeable, and an annuity
 * must match the formula. The second is subtler and matters more in production:
 * whatever the rounding does, the schedule must still add up. A loan whose
 * instalments sum to a paisa less than the total payable leaves a balance that
 * never clears, and it is a collections problem long before anyone notices it
 * was a rounding problem.
 */
class LoanCalculatorTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("50000.00");
    private static final BigDecimal NINE_PERCENT = new BigDecimal("9.000000");

    @Nested
    class FlatRate {

        @Test
        void chargesInterestOnTheWholePrincipalForTheWholeTerm() {
            // 50,000 at 9% for one year, charged flat, is 4,500 whatever is repaid
            // along the way. That is what makes flat expensive and why the method
            // is configuration rather than an assumption.
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.FLAT, RepaymentFrequency.MONTHLY);

            assertThat(result.totalInterest()).isEqualByComparingTo("4500.00");
            assertThat(result.totalPayable()).isEqualByComparingTo("54500.00");
            assertThat(result.instalment()).isEqualByComparingTo("4541.67");
        }

        @Test
        void halvesTheInterestWhenTheTermIsHalved() {
            LoanCalculator.Amortisation halfYear = LoanCalculator.amortise(
                    PRINCIPAL, 6, NINE_PERCENT, InterestMethod.FLAT, RepaymentFrequency.MONTHLY);

            assertThat(halfYear.totalInterest()).isEqualByComparingTo("2250.00");
        }

        @Test
        void everyInstalmentCarriesTheSameInterest() {
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.FLAT, RepaymentFrequency.MONTHLY);

            List<BigDecimal> interest = result.schedule().stream()
                    .map(LoanCalculator.Instalment::interest).toList();

            // The defining property of a flat rate, and the one customers are
            // most often surprised by.
            assertThat(interest).allSatisfy(part -> assertThat(part).isEqualByComparingTo("375.00"));
        }
    }

    @Nested
    class ReducingBalance {

        @Test
        void matchesTheAnnuityFormula() {
            // P·i·(1+i)^n / ((1+i)^n - 1) with i = 0.0075 and n = 12.
            // (1.0075)^12 = 1.0938068976..., so the instalment is
            // 4,372.5738384... and rounds to 4,372.57. Worked out independently
            // of the code, at thirty significant figures.
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);

            assertThat(result.instalment()).isEqualByComparingTo("4372.57");
        }

        @Test
        void costsLessThanTheSameHeadlineRateChargedFlat() {
            LoanCalculator.Amortisation reducing = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);
            LoanCalculator.Amortisation flat = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.FLAT, RepaymentFrequency.MONTHLY);

            // Roughly half, for a one-year term. If this ever inverts, the two
            // methods have been swapped somewhere.
            assertThat(reducing.totalInterest()).isLessThan(flat.totalInterest());
            assertThat(reducing.totalInterest()).isEqualByComparingTo("2470.90");
        }

        @Test
        void interestFallsAndPrincipalRisesAcrossTheSchedule() {
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);

            List<LoanCalculator.Instalment> schedule = result.schedule();
            for (int i = 1; i < schedule.size(); i++) {
                assertThat(schedule.get(i).interest())
                        .isLessThanOrEqualTo(schedule.get(i - 1).interest());
            }
            assertThat(schedule.get(0).interest()).isEqualByComparingTo("375.00");
        }

        @Test
        void treatsAZeroRateAsStraightLineRepaymentRatherThanDividingByZero() {
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    new BigDecimal("12000.00"), 12, BigDecimal.ZERO,
                    InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);

            assertThat(result.instalment()).isEqualByComparingTo("1000.00");
            assertThat(result.totalInterest()).isEqualByComparingTo("0.00");
            assertThat(result.totalPayable()).isEqualByComparingTo("12000.00");
        }
    }

    @Nested
    class EffectiveRate {

        @Test
        void unCompoundsTheAnnualRateRatherThanDividingIt() {
            // A nominal 9% divided by twelve is 0.75% a month. An effective 9%
            // is the rate actually earned over the year, so the monthly rate
            // that compounds to it is lower: 1.09^(1/12) - 1 = 0.7207%.
            BigDecimal nominal = LoanCalculator.periodicRateOf(
                    NINE_PERCENT, InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);
            BigDecimal effective = LoanCalculator.periodicRateOf(
                    NINE_PERCENT, InterestMethod.EFFECTIVE, RepaymentFrequency.MONTHLY);

            assertThat(nominal).isEqualByComparingTo("0.0075000000");
            assertThat(effective).isLessThan(nominal);
            assertThat(effective).isBetween(
                    new BigDecimal("0.0072000000"), new BigDecimal("0.0072100000"));
        }

        @Test
        void costsSlightlyLessThanTheSameNominalRate() {
            LoanCalculator.Amortisation effective = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.EFFECTIVE,
                    RepaymentFrequency.MONTHLY);
            LoanCalculator.Amortisation nominal = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);

            assertThat(effective.totalInterest()).isLessThan(nominal.totalInterest());
        }
    }

    @Nested
    class RoundingAndTheSchedule {

        /**
         * The property that has to hold whatever the rounding does, at every
         * amount and every tenure the product offers.
         */
        @ParameterizedTest
        @ValueSource(ints = {3, 6, 9, 12})
        void scheduleSumsToTheTotalPayableAndClearsTheBalance(int tenure) {
            // 13,333.33 divided by anything leaves a remainder, which is exactly
            // why it is the amount used here.
            BigDecimal awkward = new BigDecimal("13333.33");
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    awkward, tenure, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);

            BigDecimal paid = result.schedule().stream()
                    .map(LoanCalculator.Instalment::amountDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal principalRepaid = result.schedule().stream()
                    .map(LoanCalculator.Instalment::principal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(paid).isEqualByComparingTo(result.totalPayable());
            assertThat(principalRepaid).isEqualByComparingTo(awkward);
            assertThat(result.schedule().get(tenure - 1).closingBalance())
                    .isEqualByComparingTo("0.00");
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 6, 9, 12})
        void flatScheduleAlsoSumsToTheTotalPayable(int tenure) {
            BigDecimal awkward = new BigDecimal("13333.33");
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    awkward, tenure, NINE_PERCENT, InterestMethod.FLAT,
                    RepaymentFrequency.MONTHLY);

            BigDecimal paid = result.schedule().stream()
                    .map(LoanCalculator.Instalment::amountDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(paid).isEqualByComparingTo(result.totalPayable());
            assertThat(result.schedule().get(tenure - 1).closingBalance())
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void putsTheRoundingDifferenceOnTheLastInstalmentRatherThanLosingIt() {
            // The level instalment of 4,372.57 repeated twelve times does not
            // repay 50,000 at 9% exactly - it leaves six paisa. The last
            // instalment absorbs them, so the balance still reaches zero.
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT,
                    InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);

            List<LoanCalculator.Instalment> schedule = result.schedule();
            for (int i = 0; i < schedule.size() - 1; i++) {
                assertThat(schedule.get(i).amountDue()).isEqualByComparingTo(result.instalment());
            }

            LoanCalculator.Instalment last = schedule.get(schedule.size() - 1);
            assertThat(last.amountDue()).isEqualByComparingTo("4372.63");
            assertThat(last.amountDue()).isNotEqualByComparingTo(result.instalment());
            assertThat(last.closingBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        void everyFigureIsQuotedToTwoDecimalPlaces() {
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    new BigDecimal("7777.77"), 9, new BigDecimal("11.250000"),
                    InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);

            assertThat(result.instalment().scale()).isEqualTo(2);
            assertThat(result.totalInterest().scale()).isEqualTo(2);
            assertThat(result.totalPayable().scale()).isEqualTo(2);
            assertThat(result.schedule()).allSatisfy(instalment -> {
                assertThat(instalment.amountDue().scale()).isEqualTo(2);
                assertThat(instalment.principal().scale()).isEqualTo(2);
                assertThat(instalment.interest().scale()).isEqualTo(2);
                assertThat(instalment.closingBalance().scale()).isEqualTo(2);
            });
        }
    }

    @Nested
    class Frequencies {

        @Test
        void quarterlyRepaymentProducesOneInstalmentPerQuarter() {
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.QUARTERLY);

            assertThat(result.periods()).isEqualTo(4);
            assertThat(result.schedule()).hasSize(4);
            // A quarter of 9% a year, so 2.25% a quarter.
            assertThat(result.periodicRate()).isEqualByComparingTo("0.0225000000");
        }

        @Test
        void refusesATenureThatIsNotAWholeNumberOfPeriods() {
            assertThatThrownBy(() -> LoanCalculator.amortise(
                    PRINCIPAL, 5, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.QUARTERLY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("whole number");
        }
    }

    @Nested
    class Inversion {

        @Test
        void findsThePrincipalThatAnInstalmentCanRepay() {
            // The round trip: size a loan to an instalment, then quote it back.
            BigDecimal ceiling = new BigDecimal("5000.00");
            BigDecimal principal = LoanCalculator.principalAffordableAt(
                    ceiling, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);

            LoanCalculator.Amortisation quoted = LoanCalculator.amortise(
                    principal, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY);

            assertThat(quoted.instalment()).isLessThanOrEqualTo(ceiling);
        }

        @Test
        void roundsDownSoTheInstalmentNeverExceedsTheCeiling() {
            // Rounding up would produce an instalment a paisa over the limit the
            // bank has just set, which is the one direction that is not allowed.
            BigDecimal principal = LoanCalculator.principalAffordableAt(
                    new BigDecimal("3333.33"), 6, new BigDecimal("13.750000"),
                    InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);

            assertThat(principal.stripTrailingZeros().scale()).isLessThanOrEqualTo(0);

            LoanCalculator.Amortisation quoted = LoanCalculator.amortise(
                    principal, 6, new BigDecimal("13.750000"),
                    InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);
            assertThat(quoted.instalment()).isLessThanOrEqualTo(new BigDecimal("3333.33"));
        }

        @Test
        void invertsAFlatRateWithTheFlatFormula() {
            BigDecimal principal = LoanCalculator.principalAffordableAt(
                    new BigDecimal("4541.67"), 12, NINE_PERCENT, InterestMethod.FLAT,
                    RepaymentFrequency.MONTHLY);

            // The flat quotation of 50,000 over a year is 4,541.67 a month, so
            // the inversion has to give 50,000 back.
            assertThat(principal).isEqualByComparingTo("50000.00");
        }

        @Test
        void givesZeroWhenThereIsNothingToSpare() {
            assertThat(LoanCalculator.principalAffordableAt(
                    BigDecimal.ZERO, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY)).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    class RefusedInputs {

        @Test
        void refusesAZeroPrincipal() {
            assertThatThrownBy(() -> LoanCalculator.amortise(
                    BigDecimal.ZERO, 12, NINE_PERCENT, InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void refusesANegativeRate() {
            assertThatThrownBy(() -> LoanCalculator.amortise(
                    PRINCIPAL, 12, new BigDecimal("-1"), InterestMethod.REDUCING_BALANCE,
                    RepaymentFrequency.MONTHLY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void stillAmortisesAtARateNoBankWouldChargeAndNoTermIsLeftOutstanding() {
            // 300% a year over five years. The first instalment is almost
            // entirely interest - two paisa of principal - but an annuity always
            // repays, and the schedule has to prove it rather than assume it.
            // The guard inside the calculator is for a caller that ever supplies
            // its own instalment; the derived one can never trip it.
            LoanCalculator.Amortisation result = LoanCalculator.amortise(
                    PRINCIPAL, 60, new BigDecimal("300.000000"),
                    InterestMethod.REDUCING_BALANCE, RepaymentFrequency.MONTHLY);

            assertThat(result.schedule().get(0).principal()).isPositive();
            assertThat(result.schedule().get(59).closingBalance()).isEqualByComparingTo("0.00");
        }
    }
}
