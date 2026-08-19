package com.naztech.lending.eligibility.service;

import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.repository.CustomerRepository;
import com.naztech.lending.eligibility.dto.AmountDecision;
import com.naztech.lending.eligibility.dto.EligibilityRequest;
import com.naztech.lending.eligibility.dto.EligibilityResponse;
import com.naztech.lending.organization.service.OrganizationService;
import com.naztech.lending.auth.domain.RoleScope;
import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.product.repository.LoanProductVersionRepository;
import com.naztech.lending.rules.domain.RulePurpose;
import com.naztech.lending.rules.dto.RuleRunResult;
import com.naztech.lending.rules.service.RuleContext;
import com.naztech.lending.rules.service.RuleEngine;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers whether a customer may borrow, and how much.
 *
 * <p>The orchestration of §16 and §17, and nothing more: it finds the customer,
 * finds the live version of the product, assembles the facts, asks the rule
 * engine, and - only if the answer was yes - asks the amount engine. The
 * decisions themselves belong to those two, which is what keeps this class from
 * becoming the place where lending policy accumulates.
 *
 * <p>The order is deliberate. An ineligible customer is not sized: computing a
 * limit for somebody who has just been declined produces a figure that reads
 * like an offer, and somebody will eventually show it to them.
 */
@Service
public class EligibilityService {

    private final CustomerRepository customers;
    private final LoanProductVersionRepository versions;
    private final OrganizationService organization;
    private final RuleEngine rules;
    private final LoanAmountEngine amounts;
    private final Clock clock;

    public EligibilityService(CustomerRepository customers, LoanProductVersionRepository versions,
                              OrganizationService organization, RuleEngine rules,
                              LoanAmountEngine amounts, Clock clock) {
        this.customers = customers;
        this.versions = versions;
        this.organization = organization;
        this.rules = rules;
        this.amounts = amounts;
        this.clock = clock;
    }

    /**
     * Assesses one customer against one product.
     *
     * @param userId the banker asking. Their organisational scope applies here
     *               exactly as it does when they read the customer: a branch
     *               officer cannot assess somebody else's customer, and gets
     *               the same 404 they would get trying to look at them.
     */
    @Transactional(readOnly = true)
    public EligibilityResponse check(UUID userId, EligibilityRequest request) {
        Customer customer = customers.findWithAddressesByCustomerId(request.customerId())
                .filter(candidate -> isVisibleTo(userId, candidate))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such customer"));

        // Loaded inside the transaction: the rule context reads identification
        // documents, and assembling it outside would fail on a lazy collection.
        customer.getIdentifications().size();

        LoanProductVersion version = versions.findActiveByProductCode(request.productCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No such product, or it is not currently on sale"));

        LocalDate today = LocalDate.now(clock);
        RuleContext context = CustomerRuleContext.of(customer, today);

        RuleRunResult verdict = rules.run(
                customer.getId(), version.getId(), RulePurpose.ELIGIBILITY, context);

        String riskGrade = customer.getRiskProfile() == null
                ? null : customer.getRiskProfile().name();

        if (!verdict.passed()) {
            return declined(customer, version, verdict, riskGrade);
        }

        AmountDecision limits = amounts.size(customer, version, riskGrade);
        if (limits.belowMinimum()) {
            // Eligible on the criteria, but for less than the product will
            // lend. That is a decline, and the reason is a specific one worth
            // saying out loud rather than folding into the generic message.
            return new EligibilityResponse(false, customer.getCustomerId(),
                    version.getProduct().getCode(), version.getProduct().getName(),
                    version.getVersionNo(), version.getCurrency(),
                    null, null, List.of(), version.getInterestRate(),
                    version.getInterestMethod().name(), riskGrade,
                    List.of("The most you could borrow is below the minimum of %s for this product."
                            .formatted(version.getMinAmount().toPlainString())),
                    verdict.groups(), limits, verdict.evaluationId());
        }

        return new EligibilityResponse(true, customer.getCustomerId(),
                version.getProduct().getCode(), version.getProduct().getName(),
                version.getVersionNo(), version.getCurrency(),
                limits.maxAmount(), limits.recommendedAmount(), version.offeredTenures(),
                version.getInterestRate(), version.getInterestMethod().name(), riskGrade,
                List.of(), verdict.groups(), limits, verdict.evaluationId());
    }

    private EligibilityResponse declined(Customer customer, LoanProductVersion version,
                                         RuleRunResult verdict, String riskGrade) {
        return new EligibilityResponse(false, customer.getCustomerId(),
                version.getProduct().getCode(), version.getProduct().getName(),
                version.getVersionNo(), version.getCurrency(),
                null, null, List.of(), version.getInterestRate(),
                version.getInterestMethod().name(), riskGrade,
                verdict.reasons(), verdict.groups(), null, verdict.evaluationId());
    }

    /**
     * The same scope test the customer endpoints apply.
     *
     * <p>Repeated rather than shared because the two are not the same question -
     * one asks who may read a record, the other who may assess it - and a shared
     * helper would make widening one silently widen the other.
     */
    private boolean isVisibleTo(UUID userId, Customer customer) {
        if (organization.widestScopeOf(userId) == RoleScope.HEAD_OFFICE) {
            return true;
        }
        if (customer.getHomeBranch() == null) {
            return false;
        }
        return organization.visibleUnitIds(userId).contains(customer.getHomeBranch().getId());
    }
}
