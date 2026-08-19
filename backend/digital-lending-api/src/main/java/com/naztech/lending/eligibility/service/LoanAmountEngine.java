package com.naztech.lending.eligibility.service;

import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.eligibility.dto.AmountDecision;
import com.naztech.lending.eligibility.dto.LimitFactor;
import com.naztech.lending.pricing.service.LoanCalculator;
import com.naztech.lending.product.domain.LoanProductVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * How much this customer may borrow.
 *
 * <p>The specification is blunt about what not to do: "Do not use a single
 * hard-coded maximum." So the answer is the lowest of every cap that applies,
 * each computed from configuration, and each reported whether it bound or not.
 * The engine has to be able to say why - not merely what.
 *
 * <p>Six caps, as §17 lists them, and one more the specification implies. The
 * extra is the debt burden: an income multiple asks whether the customer earns
 * enough, while the debt burden ratio asks whether the instalment fits in what
 * they earn. They are different questions with different answers at long
 * tenures, and a bank that only asks the first lends more than it meant to.
 *
 * <p>Deliberately not a Spring service with dependencies - it takes a customer,
 * a product version and a day, and returns a decision. Everything it needs is an
 * argument, which is what allows a banker's "what if their income were higher"
 * to be answered without writing anything down.
 */
@Component
public class LoanAmountEngine {

    /** Loans are advanced in whole currency units; nobody borrows 30,000.37. */
    private static final int WHOLE_UNITS = 0;


    /**
     * Sizes the loan.
     *
     * @param riskProfile the customer's grade today. When the scorecard of §18
     *                    arrives it writes the same grades, so this argument
     *                    does not change - only who computes it does.
     */
    public AmountDecision size(Customer customer, LoanProductVersion version, String riskProfile) {
        List<LimitFactor> factors = new ArrayList<>();

        factors.add(productMaximum(version));
        factors.add(incomeMultiple(customer, version));
        factors.add(debtBurden(customer, version));
        factors.add(riskGrade(version, riskProfile));
        factors.add(existingExposure(customer, version));
        factors.add(regulatory(version));
        factors.add(customerSegment(version));

        LimitFactor binding = factors.stream()
                .filter(LimitFactor::applies)
                .min(Comparator.comparing(LimitFactor::amount))
                .orElseThrow(() -> new IllegalStateException(
                        "The product maximum is mandatory, so at least one factor always applies"));

        BigDecimal maxAmount = binding.amount();
        boolean belowMinimum = maxAmount.compareTo(version.getMinAmount()) < 0;

        // Marked on the winner only. Where two caps tie, the first in the list
        // is named: the order runs from the bank's own policy outwards, so a
        // customer is told about the product's limit before the regulator's.
        List<LimitFactor> reported = factors.stream()
                .map(factor -> factor == binding ? factor.asBinding() : factor)
                .toList();

        return new AmountDecision(
                maxAmount, recommend(maxAmount, version, belowMinimum),
                binding.code(), belowMinimum, reported);
    }

    /**
     * What is actually offered.
     *
     * <p>A configured share of the maximum, because a customer lent their
     * absolute ceiling has no headroom left and defaults more often. The share
     * is per version, so a bank that disagrees changes a row.
     *
     * <p>Never below the product minimum: an offer the product cannot honour is
     * not an offer. When the maximum itself is below the minimum there is no
     * loan to recommend, and the figure is zero.
     */
    private BigDecimal recommend(BigDecimal maxAmount, LoanProductVersion version,
                                 boolean belowMinimum) {
        if (belowMinimum) {
            return BigDecimal.ZERO.setScale(LoanCalculator.MONEY_SCALE);
        }
        BigDecimal offered = maxAmount.multiply(version.getRecommendedRatio())
                .setScale(WHOLE_UNITS, RoundingMode.DOWN)
                .setScale(LoanCalculator.MONEY_SCALE);
        return offered.max(version.getMinAmount()).min(maxAmount);
    }

    private LimitFactor productMaximum(LoanProductVersion version) {
        return LimitFactor.of("PRODUCT_MAX", "Product maximum",
                floor(version.getMaxAmount()),
                "%s version %d lends up to %s".formatted(
                        version.getProduct().getName(), version.getVersionNo(),
                        money(version.getMaxAmount())));
    }

    /**
     * A multiple of what the customer earns.
     *
     * <p>The most adjusted number in consumer lending, which is exactly why it
     * is a column and not a constant.
     */
    private LimitFactor incomeMultiple(Customer customer, LoanProductVersion version) {
        BigDecimal multiple = version.getIncomeMultiple();
        if (multiple == null) {
            return LimitFactor.notConfigured("INCOME_MULTIPLE", "Income based limit",
                    "This product does not cap lending by a multiple of income");
        }
        BigDecimal income = customer.totalMonthlyIncome();
        return LimitFactor.of("INCOME_MULTIPLE", "Income based limit",
                floor(income.multiply(multiple)),
                "%s times a declared monthly income of %s".formatted(
                        multiple.stripTrailingZeros().toPlainString(), money(income)));
    }

    /**
     * What the instalment can be, turned into what the principal can be.
     *
     * <p>The ratio caps the share of income that may service debt. Running the
     * amortisation backwards converts that ceiling on the instalment into a
     * ceiling on the amount, at the longest tenure the product offers - the
     * longest because it produces the largest affordable principal, and this is
     * a maximum.
     *
     * <p>No existing instalment is subtracted, because the platform does not yet
     * know of any: the loan book and the CIB feed arrive in later milestones.
     * Until they do, the whole allowance is treated as free, and the explanation
     * says so rather than implying a check that has not happened.
     */
    private LimitFactor debtBurden(Customer customer, LoanProductVersion version) {
        BigDecimal maxDbr = version.getMaxDbr();
        if (maxDbr == null) {
            return LimitFactor.notConfigured("DEBT_BURDEN", "Debt burden limit",
                    "This product does not cap lending by debt burden ratio");
        }

        BigDecimal income = customer.totalMonthlyIncome();
        BigDecimal affordableInstalment = income.multiply(maxDbr)
                .setScale(LoanCalculator.MONEY_SCALE, RoundingMode.DOWN);

        int tenure = version.getMaxTenureMonths();
        List<Integer> offered = version.offeredTenures();
        if (!offered.isEmpty()) {
            tenure = offered.get(offered.size() - 1);
        }

        BigDecimal principal = LoanCalculator.principalAffordableAt(
                affordableInstalment, tenure, version.getInterestRate(),
                version.getInterestMethod(), version.getRepaymentFrequency());

        return LimitFactor.of("DEBT_BURDEN", "Debt burden limit", floor(principal),
                ("An instalment of %s, being %s%% of a monthly income of %s, repays this much "
                        + "over %d months. No existing instalment is known to the platform yet.")
                        .formatted(money(affordableInstalment),
                                maxDbr.multiply(BigDecimal.valueOf(100)).stripTrailingZeros()
                                        .toPlainString(),
                                money(income), tenure));
    }

    /**
     * What the customer's grade is allowed.
     *
     * <p>A grade with no configured row is not capped by grade. That is a
     * decision, not an oversight: a bank that has not set a limit for a grade
     * has not said "zero", and reading silence as a refusal would decline
     * everybody the first time a new grade is introduced.
     */
    private LimitFactor riskGrade(LoanProductVersion version, String riskProfile) {
        return version.riskLimitFor(riskProfile)
                .map(cap -> LimitFactor.of("RISK_GRADE", "Credit risk limit", floor(cap),
                        "Risk grade %s may borrow up to %s under this product"
                                .formatted(riskProfile, money(cap))))
                .orElseGet(() -> LimitFactor.notConfigured("RISK_GRADE", "Credit risk limit",
                        "No limit is configured for risk grade %s".formatted(riskProfile)));
    }

    /**
     * Room left under a ceiling on total borrowing.
     *
     * <p>A concentration control, and it needs a ceiling to be configured before
     * it means anything. The product's own maximum will not do: that is what
     * this product lends, not what the borrower may owe altogether, and using it
     * would refuse a small personal loan to anybody holding a mortgage they are
     * comfortably servicing. Affordability is the debt burden ratio's job.
     *
     * <p>Declared liabilities are all the platform can see today. When the loan
     * book and the CIB feed arrive they replace the declaration; the shape of
     * this calculation does not change.
     *
     * <p>A negative result becomes zero: somebody already past the ceiling has
     * no room, not negative room.
     */
    private LimitFactor existingExposure(Customer customer, LoanProductVersion version) {
        BigDecimal ceiling = version.getMaxTotalExposure();
        if (ceiling == null) {
            return LimitFactor.notConfigured("EXISTING_EXPOSURE", "Existing exposure limit",
                    "This product sets no ceiling on total borrowing");
        }
        BigDecimal existing = customer.getExistingLiabilities();
        if (existing == null || existing.signum() == 0) {
            return LimitFactor.of("EXISTING_EXPOSURE", "Existing exposure limit", floor(ceiling),
                    "No existing borrowing is declared, against a total ceiling of %s"
                            .formatted(money(ceiling)));
        }
        BigDecimal headroom = ceiling.subtract(existing).max(BigDecimal.ZERO);
        return LimitFactor.of("EXISTING_EXPOSURE", "Existing exposure limit", floor(headroom),
                "Declared borrowing of %s against a total ceiling of %s".formatted(
                        money(existing), money(ceiling)));
    }

    private LimitFactor regulatory(LoanProductVersion version) {
        BigDecimal cap = version.getRegulatoryMaxAmount();
        if (cap == null) {
            return LimitFactor.notConfigured("REGULATORY", "Regulatory limit",
                    "No regulatory ceiling is configured for this product");
        }
        return LimitFactor.of("REGULATORY", "Regulatory limit", floor(cap),
                "A regulatory ceiling of %s applies to this product".formatted(money(cap)));
    }

    /**
     * The segment cap.
     *
     * <p>Reported as unconfigured rather than omitted. §17 names it, and a
     * factor that silently disappears from the list is indistinguishable from
     * one that was forgotten - so it says plainly that nothing constrains here.
     * A version restricted to a segment excludes customers through the rule
     * engine, which is where a yes-or-no belongs; this factor exists for a cap
     * on amount, and no product configures one today.
     */
    private LimitFactor customerSegment(LoanProductVersion version) {
        return LimitFactor.notConfigured("CUSTOMER_SEGMENT", "Customer segment limit",
                "ANY".equals(version.getCustomerSegment())
                        ? "This product version is open to every segment"
                        : "Segment %s is not capped by amount".formatted(version.getCustomerSegment()));
    }

    /** Whole currency units, always downwards: a cap that rounds up is not a cap. */
    private BigDecimal floor(BigDecimal amount) {
        return amount.setScale(WHOLE_UNITS, RoundingMode.DOWN)
                .setScale(LoanCalculator.MONEY_SCALE);
    }

    /**
     * An amount as a person reads it.
     *
     * <p>Formatted with an explicit locale rather than a shared DecimalFormat:
     * that class is not thread safe, and this one is a singleton serving
     * concurrent requests. Grouping is by thousands because these strings are
     * read by bankers, not parsed by clients - the machine-readable figure is
     * the BigDecimal beside them.
     */
    private String money(BigDecimal amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }
}
