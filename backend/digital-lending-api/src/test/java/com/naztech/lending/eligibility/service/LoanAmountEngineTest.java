package com.naztech.lending.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.domain.CustomerType;
import com.naztech.lending.eligibility.dto.AmountDecision;
import com.naztech.lending.eligibility.dto.LimitFactor;
import com.naztech.lending.product.domain.InterestMethod;
import com.naztech.lending.product.domain.LoanProduct;
import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.product.domain.RepaymentFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The loan amount engine of §17.
 *
 * <p>Two obligations, and the second is the one that is easy to skip. The engine
 * has to produce the lowest of every applicable cap - and it has to be able to
 * say which one it was and how it was arrived at. A customer told they may
 * borrow thirty thousand against an advertised fifty is owed that answer, and a
 * banker asked for it should not have to reconstruct the arithmetic.
 *
 * <p>The worked example in the specification is reproduced below, figure for
 * figure.
 */
class LoanAmountEngineTest {

    private static final LoanAmountEngine ENGINE = new LoanAmountEngine();

    private static final BigDecimal TEN = new BigDecimal("10.0000");
    private static final BigDecimal HALF = new BigDecimal("0.5000");
    private static final BigDecimal SEVENTY_PERCENT = new BigDecimal("0.7000");

    @Test
    void reproducesTheWorkedExampleFromTheSpecification() {
        // Product max 50,000; income limit 40,000; risk limit 35,000; existing
        // exposure limit 30,000. Final eligible amount 30,000.
        LoanProductVersion version = eLoan()
                .withLimits(TEN, null, null, SEVENTY_PERCENT, new BigDecimal("50000.0000"))
                .withRiskLimit("MEDIUM", new BigDecimal("35000.0000"));

        Customer customer = customerEarning("4000.0000");
        customer.describeFinances(new BigDecimal("4000.0000"), null, null, null, null,
                new BigDecimal("20000.0000"));

        AmountDecision decision = ENGINE.size(customer, version, "MEDIUM");

        assertThat(factor(decision, "PRODUCT_MAX").amount()).isEqualByComparingTo("50000.00");
        assertThat(factor(decision, "INCOME_MULTIPLE").amount()).isEqualByComparingTo("40000.00");
        assertThat(factor(decision, "RISK_GRADE").amount()).isEqualByComparingTo("35000.00");
        assertThat(factor(decision, "EXISTING_EXPOSURE").amount()).isEqualByComparingTo("30000.00");

        assertThat(decision.maxAmount()).isEqualByComparingTo("30000.00");
        assertThat(decision.bindingFactor()).isEqualTo("EXISTING_EXPOSURE");
        assertThat(decision.belowMinimum()).isFalse();
    }

    @Test
    void namesTheCapThatBoundAndOnlyThatOne() {
        LoanProductVersion version = eLoan()
                .withLimits(TEN, null, null, null, null)
                .withRiskLimit("HIGH", new BigDecimal("15000.0000"));

        AmountDecision decision = ENGINE.size(customerEarning("100000.0000"), version, "HIGH");

        assertThat(decision.bindingFactor()).isEqualTo("RISK_GRADE");
        assertThat(decision.factors()).filteredOn(LimitFactor::binding).hasSize(1);
        assertThat(factor(decision, "RISK_GRADE").binding()).isTrue();
    }

    @Test
    void reportsEveryFactorEvenTheOnesThatDidNotApply() {
        // The specification asks the engine to explain itself. A factor that
        // silently vanishes from the list is indistinguishable from one that was
        // forgotten, so all seven are always present.
        AmountDecision decision = ENGINE.size(
                customerEarning("50000.0000"), eLoan(), "MEDIUM");

        assertThat(decision.factors()).extracting(LimitFactor::code).containsExactly(
                "PRODUCT_MAX", "INCOME_MULTIPLE", "DEBT_BURDEN", "RISK_GRADE",
                "EXISTING_EXPOSURE", "REGULATORY", "CUSTOMER_SEGMENT");
        assertThat(decision.factors()).allSatisfy(
                factor -> assertThat(factor.explanation()).isNotBlank());
    }

    @Test
    void anUnconfiguredCapConstrainsNothingRatherThanCappingAtZero() {
        // A grade with no configured row is not a grade limited to nothing. The
        // opposite reading would decline everybody the first time a bank
        // introduced a new grade.
        LoanProductVersion version = eLoan().withRiskLimit("LOW", new BigDecimal("50000.0000"));

        AmountDecision decision = ENGINE.size(customerEarning("50000.0000"), version, "HIGH");

        assertThat(factor(decision, "RISK_GRADE").amount()).isNull();
        assertThat(factor(decision, "RISK_GRADE").applies()).isFalse();
        assertThat(decision.maxAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void turnsTheDebtBurdenRatioIntoALimitOnTheAmount() {
        // Half of a 3,000 income is 1,500 a month. At 9% reducing over twelve
        // months an instalment of 1,500 repays 17,152 - the annuity run
        // backwards - so the ratio caps the loan well below both the product
        // maximum and ten times the income.
        LoanProductVersion version = eLoan().withLimits(TEN, HALF, null, null, null);

        AmountDecision decision = ENGINE.size(customerEarning("3000.0000"), version, "MEDIUM");

        assertThat(factor(decision, "DEBT_BURDEN").amount()).isEqualByComparingTo("17152.00");
        assertThat(decision.bindingFactor()).isEqualTo("DEBT_BURDEN");
        assertThat(factor(decision, "DEBT_BURDEN").explanation())
                .contains("1,500.00")
                .contains("No existing instalment is known");
    }

    @Test
    void sizesTheDebtBurdenAtTheLongestTenureBecauseThisIsAMaximum() {
        LoanProductVersion version = eLoan().withLimits(null, HALF, null, null, null);

        AmountDecision decision = ENGINE.size(customerEarning("3000.0000"), version, "MEDIUM");

        assertThat(factor(decision, "DEBT_BURDEN").explanation()).contains("over 12 months");
    }

    @Test
    void offersAConfiguredShareOfTheMaximumRatherThanTheMaximum() {
        LoanProductVersion version = eLoan()
                .withLimits(null, null, null, SEVENTY_PERCENT, null);

        AmountDecision decision = ENGINE.size(customerEarning("100000.0000"), version, "MEDIUM");

        assertThat(decision.maxAmount()).isEqualByComparingTo("50000.00");
        assertThat(decision.recommendedAmount()).isEqualByComparingTo("35000.00");
    }

    @Test
    void neverRecommendsBelowWhatTheProductWillLend() {
        // 5,001 at seventy percent is 3,500, which the product cannot advance.
        // The offer is raised to the minimum rather than made and then refused.
        LoanProductVersion version = eLoan()
                .withLimits(null, null, new BigDecimal("5001.0000"), SEVENTY_PERCENT, null);

        AmountDecision decision = ENGINE.size(customerEarning("100000.0000"), version, "MEDIUM");

        assertThat(decision.maxAmount()).isEqualByComparingTo("5001.00");
        assertThat(decision.recommendedAmount()).isEqualByComparingTo("5000.0000");
    }

    @Test
    void flagsAMaximumThatFallsBelowTheProductMinimum() {
        // Ten times an income of 400 is 4,000, and the product does not lend
        // below 5,000. That is a decline, not a small loan.
        LoanProductVersion version = eLoan().withLimits(TEN, null, null, null, null);

        AmountDecision decision = ENGINE.size(customerEarning("400.0000"), version, "MEDIUM");

        assertThat(decision.maxAmount()).isEqualByComparingTo("4000.00");
        assertThat(decision.belowMinimum()).isTrue();
        assertThat(decision.recommendedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void roundsEveryCapDownSoNoCapIsEverExceeded() {
        // Three and a half times an income of 1,111.11 is 3,888.885. Rounding up
        // would produce a limit above the one the bank configured.
        LoanProductVersion version = eLoan()
                .withLimits(new BigDecimal("3.5000"), null, null, null, null);

        AmountDecision decision = ENGINE.size(customerEarning("1111.1100"), version, "MEDIUM");

        assertThat(factor(decision, "INCOME_MULTIPLE").amount()).isEqualByComparingTo("3888.00");
    }

    @Test
    void countsBothPartsOfDeclaredIncome() {
        Customer customer = individual();
        customer.describeFinances(new BigDecimal("2000.0000"), new BigDecimal("1000.0000"),
                null, null, null, null);

        LoanProductVersion version = eLoan().withLimits(TEN, null, null, null, null);

        assertThat(factor(ENGINE.size(customer, version, "MEDIUM"), "INCOME_MULTIPLE").amount())
                .isEqualByComparingTo("30000.00");
    }

    @Test
    void doesNotCountBorrowingElsewhereWhenNoExposureCeilingIsConfigured() {
        // The case that matters in practice. A customer earning 135,000 a month
        // with an 850,000 mortgage is exactly who a bank wants to lend 50,000
        // to. Reading their mortgage as a bar on a small personal loan - which
        // taking the product's own maximum as the ceiling would do - refuses the
        // best borrower on the book. Concentration is a configured control;
        // affordability is the debt burden ratio's job.
        Customer customer = individual();
        customer.describeFinances(new BigDecimal("120000.0000"), new BigDecimal("15000.0000"),
                null, null, null, new BigDecimal("850000.0000"));

        AmountDecision decision = ENGINE.size(customer, eLoan(), "LOW");

        assertThat(factor(decision, "EXISTING_EXPOSURE").applies()).isFalse();
        assertThat(factor(decision, "EXISTING_EXPOSURE").explanation())
                .contains("no ceiling on total borrowing");
        assertThat(decision.maxAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void capsAtTheWholeCeilingWhenNothingIsOwedElsewhere() {
        LoanProductVersion version = eLoan()
                .withLimits(null, null, null, null, new BigDecimal("40000.0000"));

        AmountDecision decision = ENGINE.size(customerEarning("100000.0000"), version, "MEDIUM");

        assertThat(factor(decision, "EXISTING_EXPOSURE").amount()).isEqualByComparingTo("40000.00");
        assertThat(decision.maxAmount()).isEqualByComparingTo("40000.00");
    }

    @Test
    void treatsBorrowingPastTheCeilingAsNoRoomRatherThanNegativeRoom() {
        LoanProductVersion version = eLoan()
                .withLimits(null, null, null, null, new BigDecimal("50000.0000"));

        Customer customer = customerEarning("100000.0000");
        customer.describeFinances(new BigDecimal("100000.0000"), null, null, null, null,
                new BigDecimal("80000.0000"));

        AmountDecision decision = ENGINE.size(customer, version, "MEDIUM");

        assertThat(factor(decision, "EXISTING_EXPOSURE").amount()).isEqualByComparingTo("0.00");
        assertThat(decision.maxAmount()).isEqualByComparingTo("0.00");
        assertThat(decision.belowMinimum()).isTrue();
    }

    private static LimitFactor factor(AmountDecision decision, String code) {
        return decision.factors().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No factor called " + code));
    }

    /**
     * e-Loan as the migration seeds it, minus the limits each test sets for
     * itself. Built through the domain's own construction API rather than by
     * reflection, so a change to the entity breaks this loudly.
     */
    private static LoanProductVersion eLoan() {
        LoanProduct product = LoanProduct.of("ELOAN", "e-Loan", null, "TERM_LOAN",
                "PERSONAL", null, "test");
        Set<Short> tenures = new LinkedHashSet<>(Set.of((short) 3, (short) 6, (short) 9, (short) 12));
        return LoanProductVersion.initial(product, LocalDate.of(2026, 1, 1), "BDT",
                new BigDecimal("5000.0000"), new BigDecimal("50000.0000"), tenures,
                InterestMethod.REDUCING_BALANCE, new BigDecimal("9.000000"),
                RepaymentFrequency.MONTHLY, "test");
    }

    private static Customer customerEarning(String monthlyIncome) {
        Customer customer = individual();
        customer.describeFinances(new BigDecimal(monthlyIncome), null, null, null, null, null);
        return customer;
    }

    private static Customer individual() {
        return new Customer("CUS-100001", CustomerType.INDIVIDUAL, "Test Customer", "01700000000");
    }
}
