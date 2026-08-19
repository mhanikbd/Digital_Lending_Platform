package com.naztech.lending.eligibility.service;

import com.naztech.lending.customer.domain.AddressType;
import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.domain.CustomerAddress;
import com.naztech.lending.customer.domain.CustomerIdentification;
import com.naztech.lending.customer.domain.IdentificationType;
import com.naztech.lending.rules.service.RuleContext;
import java.time.LocalDate;

/**
 * Turns a customer into the facts the rule engine tests.
 *
 * <p>The one place that knows both halves. The rules module must not import the
 * customer module - it would then be a rules-about-customers module, and the
 * next subject type would need a second copy of it - and the customer module has
 * no business knowing rules exist. So the bridge lives in eligibility, which is
 * the thing that actually wants both.
 *
 * <p>The attribute codes here are the ones seeded in {@code t_rule_attribute}. A
 * code in the catalogue that this method does not populate is an attribute no
 * rule can usefully test, which is why the catalogue was seeded with only what
 * the platform can supply today.
 */
public final class CustomerRuleContext {

    private CustomerRuleContext() {
    }

    /**
     * @param today the day the decision is being made on, because age is not a
     *              property of a person but of a person and a date
     */
    public static RuleContext of(Customer customer, LocalDate today) {
        return RuleContext.builder()
                .number("customer.age", customer.ageOn(today).orElse(null))
                .number("customer.monthly_income", customer.totalMonthlyIncome())
                .number("customer.existing_liabilities", customer.getExistingLiabilities())
                .number("customer.net_worth", customer.getNetWorth())
                .text("customer.kyc_status", customer.getKycStatus())
                .text("customer.risk_profile", customer.getRiskProfile())
                .text("customer.type", customer.getCustomerType())
                .text("customer.status", customer.getStatus())
                .text("customer.residence_status", customer.getResidenceStatus())
                .text("customer.occupation", customer.getOccupation())
                .text("customer.district", presentDistrictOf(customer))
                .flag("customer.has_verified_nid", hasVerifiedNid(customer))
                .build();
    }

    /**
     * The district a customer lives in, from their present address.
     *
     * <p>The permanent address is not a fallback. Lending policy that varies by
     * district varies by where somebody actually is, and a village of origin
     * would answer a different question from the one being asked.
     */
    private static String presentDistrictOf(Customer customer) {
        // The address is found first and read second. Mapping before findFirst
        // puts a possibly-null district inside the stream, and Optional refuses
        // to hold null - a customer living abroad with no district recorded
        // would abort the whole assessment rather than fail one rule.
        return customer.getAddresses().stream()
                .filter(address -> address.getAddressType() == AddressType.PRESENT)
                .findFirst()
                .map(CustomerAddress::getDistrict)
                .orElse(null);
    }

    private static boolean hasVerifiedNid(Customer customer) {
        return customer.getIdentifications().stream()
                .filter(id -> id.getIdType() == IdentificationType.NID)
                .anyMatch(CustomerIdentification::isVerified);
    }
}
