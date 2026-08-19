package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * The financial picture the decision was taken on.
 *
 * <p>Declared figures, kept as declared. The debt burden ratio is computed once
 * at submission and stored, so the ratio on the file is the ratio the approver
 * saw rather than one recalculated later from numbers that have moved.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_financial")
public class ApplicationFinancial {

    @Id
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private LoanApplication application;

    @Column(name = "monthly_income", nullable = false, precision = 20, scale = 4)
    private BigDecimal monthlyIncome = BigDecimal.ZERO;

    @Column(name = "other_monthly_income", nullable = false, precision = 20, scale = 4)
    private BigDecimal otherMonthlyIncome = BigDecimal.ZERO;

    @Column(name = "monthly_expense", nullable = false, precision = 20, scale = 4)
    private BigDecimal monthlyExpense = BigDecimal.ZERO;

    @Column(name = "existing_liabilities", nullable = false, precision = 20, scale = 4)
    private BigDecimal existingLiabilities = BigDecimal.ZERO;

    /** What the existing borrowing costs each month, which is what a ratio needs. */
    @Column(name = "existing_emi", nullable = false, precision = 20, scale = 4)
    private BigDecimal existingEmi = BigDecimal.ZERO;

    @Column(name = "net_worth", precision = 20, scale = 4)
    private BigDecimal netWorth;

    @Column(name = "source_of_income", length = 120)
    private String sourceOfIncome;

    @Column(name = "source_of_funds", length = 120)
    private String sourceOfFunds;

    @Column(name = "debt_burden_ratio", precision = 9, scale = 6)
    private BigDecimal debtBurdenRatio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ApplicationFinancial() {
        // for JPA
    }

    public ApplicationFinancial(BigDecimal monthlyIncome, BigDecimal otherMonthlyIncome,
                                BigDecimal monthlyExpense, BigDecimal existingLiabilities,
                                BigDecimal existingEmi) {
        this.monthlyIncome = orZero(monthlyIncome);
        this.otherMonthlyIncome = orZero(otherMonthlyIncome);
        this.monthlyExpense = orZero(monthlyExpense);
        this.existingLiabilities = orZero(existingLiabilities);
        this.existingEmi = orZero(existingEmi);
    }

    void attachTo(LoanApplication owner) {
        this.application = owner;
    }

    public void describeSources(BigDecimal netWorth, String sourceOfIncome, String sourceOfFunds) {
        this.netWorth = netWorth;
        this.sourceOfIncome = sourceOfIncome;
        this.sourceOfFunds = sourceOfFunds;
    }

    public BigDecimal totalMonthlyIncome() {
        return monthlyIncome.add(otherMonthlyIncome);
    }

    /**
     * The share of income that would go to servicing debt if this loan were
     * written, counting what the applicant already pays.
     *
     * <p>Zero income gives no ratio rather than a division by zero: somebody who
     * declares nothing is not somebody with an infinite burden, they are somebody
     * the eligibility rules decline for a different reason entirely.
     */
    public BigDecimal computeDebtBurdenRatio(BigDecimal proposedInstalment) {
        BigDecimal income = totalMonthlyIncome();
        if (income.signum() == 0) {
            this.debtBurdenRatio = null;
            return null;
        }
        BigDecimal committed = existingEmi.add(orZero(proposedInstalment));
        this.debtBurdenRatio = committed.divide(income, 6, RoundingMode.HALF_UP);
        return this.debtBurdenRatio;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getOtherMonthlyIncome() {
        return otherMonthlyIncome;
    }

    public BigDecimal getMonthlyExpense() {
        return monthlyExpense;
    }

    public BigDecimal getExistingLiabilities() {
        return existingLiabilities;
    }

    public BigDecimal getExistingEmi() {
        return existingEmi;
    }

    public BigDecimal getNetWorth() {
        return netWorth;
    }

    public String getSourceOfIncome() {
        return sourceOfIncome;
    }

    public String getSourceOfFunds() {
        return sourceOfFunds;
    }

    public BigDecimal getDebtBurdenRatio() {
        return debtBurdenRatio;
    }
}
