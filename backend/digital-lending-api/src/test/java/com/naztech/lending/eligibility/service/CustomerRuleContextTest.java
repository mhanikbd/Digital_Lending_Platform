package com.naztech.lending.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.customer.domain.AddressType;
import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.domain.CustomerAddress;
import com.naztech.lending.customer.domain.CustomerIdentification;
import com.naztech.lending.customer.domain.CustomerType;
import com.naztech.lending.customer.domain.IdentificationType;
import com.naztech.lending.customer.domain.KycStatus;
import com.naztech.lending.customer.domain.RiskProfile;
import com.naztech.lending.rules.service.RuleContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The bridge between a customer and the facts the rule engine tests.
 *
 * <p>Its job is to be complete and to never throw. A rule that cannot be applied
 * has to decline the applicant with a recorded reason; an exception here would
 * abandon the whole assessment and take every other criterion with it, which is
 * how one missing field turns into a customer who cannot be assessed at all.
 */
class CustomerRuleContextTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test
    void suppliesEveryAttributeTheSeededCatalogueDeclares() {
        // A code in t_rule_attribute that this never populates is an attribute
        // no rule can usefully test. The list is the migration's, verbatim.
        RuleContext context = CustomerRuleContext.of(fullyDescribed(), TODAY);

        assertThat(context.snapshot()).containsOnlyKeys(
                "customer.age",
                "customer.monthly_income",
                "customer.existing_liabilities",
                "customer.net_worth",
                "customer.kyc_status",
                "customer.risk_profile",
                "customer.type",
                "customer.status",
                "customer.residence_status",
                "customer.occupation",
                "customer.district",
                "customer.has_verified_nid");
    }

    @Test
    void readsTheFactsOffTheCustomer() {
        RuleContext context = CustomerRuleContext.of(fullyDescribed(), TODAY);

        assertThat(context.render("customer.age")).isEqualTo("38");
        assertThat(context.render("customer.monthly_income")).isEqualTo("135000.0000");
        assertThat(context.render("customer.kyc_status")).isEqualTo("VERIFIED");
        assertThat(context.render("customer.district")).isEqualTo("Dhaka");
        assertThat(context.render("customer.has_verified_nid")).isEqualTo("true");
    }

    @Test
    void takesTheDistrictFromWhereTheCustomerLivesRatherThanWhereTheyAreFrom() {
        // Lending policy that varies by district varies by where somebody
        // actually is. A village of origin answers a different question.
        Customer customer = individual();
        customer.addAddress(new CustomerAddress(AddressType.PERMANENT, "Village", "Faridpur", "Faridpur"));
        customer.addAddress(new CustomerAddress(AddressType.PRESENT, "Flat 4B", "Dhaka", "Dhaka"));

        assertThat(CustomerRuleContext.of(customer, TODAY).render("customer.district"))
                .isEqualTo("Dhaka");
    }

    @Test
    void survivesAPresentAddressWithNoDistrict() {
        // A customer living abroad. Mapping the district before finding the
        // address puts a null inside an Optional, which throws - and took the
        // whole assessment with it rather than failing the one rule that tests
        // district.
        Customer customer = individual();
        customer.addAddress(new CustomerAddress(AddressType.PRESENT, "Marina", "Dubai", null));

        RuleContext context = CustomerRuleContext.of(customer, TODAY);

        assertThat(context.has("customer.district")).isTrue();
        assertThat(context.render("customer.district")).isNull();
    }

    @Test
    void survivesACustomerWithNoAddressAtAll() {
        RuleContext context = CustomerRuleContext.of(individual(), TODAY);

        assertThat(context.has("customer.district")).isTrue();
        assertThat(context.render("customer.district")).isNull();
    }

    @Test
    void reportsAnUndeclaredIncomeAsZeroRatherThanAsUnknown() {
        // The customer entity is deliberate about this: nothing declared is
        // nothing earned as far as a limit is concerned.
        assertThat(CustomerRuleContext.of(individual(), TODAY).render("customer.monthly_income"))
                .isEqualTo("0");
    }

    @Test
    void recordsAnAbsentDateOfBirthAsUnknownRatherThanAsZero() {
        // Zero would be a plausible-looking age that silently fails an
        // over-21 rule. Null makes the evaluator say it could not be determined.
        RuleContext context = CustomerRuleContext.of(individual(), TODAY);

        assertThat(context.has("customer.age")).isTrue();
        assertThat(context.render("customer.age")).isNull();
    }

    @Test
    void countsOnlyAVerifiedNationalIdAsVerified() {
        Customer unverified = individual();
        unverified.addIdentification(new CustomerIdentification(IdentificationType.NID, "1234567890"));

        assertThat(CustomerRuleContext.of(unverified, TODAY).render("customer.has_verified_nid"))
                .isEqualTo("false");
    }

    @Test
    void doesNotMistakeAVerifiedPassportForAVerifiedNationalId() {
        Customer customer = individual();
        CustomerIdentification passport =
                new CustomerIdentification(IdentificationType.PASSPORT, "BX0912345");
        passport.markVerified(Instant.parse("2026-01-01T00:00:00Z"));
        customer.addIdentification(passport);

        assertThat(CustomerRuleContext.of(customer, TODAY).render("customer.has_verified_nid"))
                .isEqualTo("false");
    }

    private static Customer fullyDescribed() {
        Customer customer = individual();
        customer.describePerson(null, null, null, LocalDate.of(1988, 1, 15), null, null, null);
        customer.describeFinances(new BigDecimal("120000.0000"), new BigDecimal("15000.0000"),
                "Salary", "Employment", new BigDecimal("4500000.0000"),
                new BigDecimal("850000.0000"));
        customer.describeStanding(RiskProfile.LOW, KycStatus.VERIFIED, "customer@example.com");
        customer.describeOccupation("Service", "Manager", "Acme Ltd");
        customer.addAddress(new CustomerAddress(AddressType.PRESENT, "Flat 4B", "Dhaka", "Dhaka"));

        CustomerIdentification nid = new CustomerIdentification(IdentificationType.NID, "1234567890");
        nid.markVerified(Instant.parse("2026-01-01T00:00:00Z"));
        customer.addIdentification(nid);

        return customer;
    }

    private static Customer individual() {
        return new Customer("CIF-000001", CustomerType.INDIVIDUAL, "Rahim Uddin Ahmed", "01711000001");
    }
}
