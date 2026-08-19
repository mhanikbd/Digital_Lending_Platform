package com.naztech.lending.pricing.service;

import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.pricing.dto.FeeLine;
import com.naztech.lending.pricing.dto.InstalmentLine;
import com.naztech.lending.pricing.dto.LoanQuote;
import com.naztech.lending.pricing.dto.LoanQuoteRequest;
import com.naztech.lending.product.domain.FeeCollectionPoint;
import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.product.domain.ProductFee;
import com.naztech.lending.product.repository.LoanProductVersionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns product configuration into a quotation.
 *
 * <p>The division of labour matters. {@link LoanCalculator} knows arithmetic and
 * nothing else; this class knows where the rate comes from, which fees apply and
 * what the product will accept. Neither knows what the other does, so the
 * arithmetic can be tested exhaustively without a database and the configuration
 * can change without touching a formula.
 *
 * <p>The rate is never taken from the request unless the caller was granted the
 * right to negotiate one, and a negotiated rate is flagged in the quote. A
 * quotation whose rate cannot be traced to either the product or a named
 * concession is not a quotation the bank can stand behind.
 */
@Service
public class PricingService {

    private final LoanProductVersionRepository versions;

    public PricingService(LoanProductVersionRepository versions) {
        this.versions = versions;
    }

    /**
     * Quotes the live version of a product.
     *
     * @param mayNegotiate whether the caller holds {@code product.price}; a
     *                     rate override from anyone else is refused rather than
     *                     ignored, because silently quoting a different rate
     *                     from the one asked for is worse than saying no
     */
    @Transactional(readOnly = true)
    public LoanQuote quote(LoanQuoteRequest request, boolean mayNegotiate) {
        LoanProductVersion version = versions.findActiveByProductCode(request.productCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No such product, or it is not currently on sale"));

        BigDecimal amount = request.amount().setScale(LoanCalculator.MONEY_SCALE, RoundingMode.HALF_UP);
        int tenure = request.tenureMonths();

        if (!version.acceptsAmount(amount)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "%s is available between %s and %s".formatted(
                            version.getProduct().getName(),
                            version.getMinAmount().toPlainString(),
                            version.getMaxAmount().toPlainString()));
        }
        if (!version.acceptsTenure(tenure)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "%s is offered over %s months".formatted(
                            version.getProduct().getName(),
                            version.offeredTenures().stream().map(String::valueOf)
                                    .collect(java.util.stream.Collectors.joining(", "))));
        }

        boolean negotiated = request.rateOverride() != null;
        if (negotiated && !mayNegotiate) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "You are not permitted to quote a negotiated rate");
        }
        BigDecimal rate = negotiated ? request.rateOverride() : version.getInterestRate();

        return build(version, amount, tenure, rate, negotiated);
    }

    /**
     * The quotation itself, once the terms are settled.
     *
     * <p>Kept separate from the lookup so the eligibility engine can quote the
     * amount it just computed without going back to the database for a version
     * it is already holding.
     */
    public LoanQuote build(LoanProductVersion version, BigDecimal principal, int tenureMonths,
                           BigDecimal annualRate, boolean negotiated) {
        LoanCalculator.Amortisation amortisation;
        try {
            amortisation = LoanCalculator.amortise(principal, tenureMonths, annualRate,
                    version.getInterestMethod(), version.getRepaymentFrequency());
        } catch (IllegalArgumentException impossible) {
            // The arithmetic refuses inputs it cannot amortise. That is a
            // rejected request, not a server fault.
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, impossible.getMessage());
        }

        List<FeeLine> fees = version.getFees().stream()
                // Penalties are not part of a quotation. Quoting a late payment
                // charge to somebody who has not yet borrowed states a cost they
                // will only pay if they default, which is not a cost of the loan.
                .filter(fee -> fee.getCollectedAt() != FeeCollectionPoint.ON_DEFAULT)
                .sorted(java.util.Comparator.comparing(ProductFee::getFeeCode))
                .map(fee -> FeeLine.of(fee, principal))
                .toList();

        BigDecimal totalFees = sum(fees.stream().map(FeeLine::amount));
        BigDecimal totalVat = sum(fees.stream().map(FeeLine::vat));

        BigDecimal deductedAtDisbursement = sum(fees.stream()
                .filter(fee -> FeeCollectionPoint.DISBURSEMENT.name().equals(fee.collectedAt()))
                .map(FeeLine::total));

        BigDecimal totalPayable = amortisation.totalPayable().add(totalFees).add(totalVat);
        BigDecimal netDisbursement = principal.subtract(deductedAtDisbursement);

        return new LoanQuote(
                version.getProduct().getCode(),
                version.getProduct().getName(),
                version.getVersionNo(),
                version.getCurrency(),
                principal,
                tenureMonths,
                amortisation.periods(),
                version.getRepaymentFrequency().name(),
                annualRate,
                version.getInterestMethod().name(),
                negotiated,
                amortisation.instalment(),
                amortisation.totalInterest(),
                totalFees,
                totalVat,
                totalPayable,
                netDisbursement,
                fees,
                amortisation.schedule().stream().map(InstalmentLine::of).toList());
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(LoanCalculator.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
