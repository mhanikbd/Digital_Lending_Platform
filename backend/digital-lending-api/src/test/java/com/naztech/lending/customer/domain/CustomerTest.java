package com.naztech.lending.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The customer master's own rules.
 *
 * <p>Small, but each one is read by something that decides money later: age and
 * income feed eligibility, and a customer who cannot borrow must not reach a
 * limit calculation at all.
 */
class CustomerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test
    void totalIncomeAddsBothPartsWithoutLosingPrecision() {
        Customer customer = individual();
        customer.describeFinances(
                new BigDecimal("120000.5000"), new BigDecimal("15000.2500"),
                "Salary", "Employment", null, null);

        // Exact, because these are BigDecimal against NUMERIC(20,4). The same sum
        // in doubles is 135000.75000000001.
        assertThat(customer.totalMonthlyIncome()).isEqualByComparingTo(new BigDecimal("135000.7500"));
    }

    @Test
    void totalIncomeIsZeroRatherThanNullWhenNothingIsDeclared() {
        // Every caller adds this to an expense figure. Returning null would make
        // all of them write the same guard, and one of them would forget.
        assertThat(individual().totalMonthlyIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void totalIncomeCountsThePartThatIsPresentWhenTheOtherIsNot() {
        Customer customer = individual();
        customer.describeFinances(new BigDecimal("90000.0000"), null, null, null, null, null);

        assertThat(customer.totalMonthlyIncome()).isEqualByComparingTo(new BigDecimal("90000.0000"));
    }

    @Test
    void ageIsDerivedFromTheDateOfBirthSoItIsRightTheDayAfterABirthday() {
        Customer customer = individual();
        customer.describePerson(null, null, null, LocalDate.of(1988, 8, 20), null, null, null);

        // The day before the birthday, and the day of it.
        assertThat(customer.ageOn(TODAY)).contains(37);
        assertThat(customer.ageOn(TODAY.plusDays(1))).contains(38);
    }

    @Test
    void ageIsAbsentRatherThanZeroWhenNoDateOfBirthIsHeld() {
        // Zero would be a plausible-looking age that fails an over-18 rule
        // silently. Absent forces the caller to decide what to do about it.
        assertThat(individual().ageOn(TODAY)).isEmpty();
    }

    @Test
    void aMinorCannotBorrowEvenWhileTheirRelationshipIsActive() {
        Customer minor = new Customer("CIF-000099", CustomerType.MINOR, "Young Person", "01712345699");

        assertThat(minor.getStatus().canTransact()).isTrue();
        assertThat(minor.isEligibleToBorrow()).isFalse();
    }

    @Test
    void anIndividualWhoseRelationshipIsLiveCanBorrow() {
        assertThat(individual().isEligibleToBorrow()).isTrue();
    }

    @Test
    void aBusinessIsNotANaturalPersonButMayStillBorrow() {
        Customer business = new Customer("CIF-000098", CustomerType.BUSINESS, "Hasan Trading", "01712345698");

        assertThat(business.getCustomerType().isNaturalPerson()).isFalse();
        assertThat(business.isEligibleToBorrow()).isTrue();
    }

    @Test
    void aSoleProprietorIsBothABusinessAndAPerson() {
        // The distinction matters for documents: a sole proprietor presents an
        // NID as well as a trade licence, because the two are the same party.
        assertThat(CustomerType.SOLE_PROPRIETOR.isNaturalPerson()).isTrue();
    }

    @Test
    void anIdentificationNumberIsFoundByItsKind() {
        Customer customer = individual();
        customer.addIdentification(new CustomerIdentification(IdentificationType.NID, "1990312456"));
        customer.addIdentification(new CustomerIdentification(IdentificationType.TIN, "412563789012"));

        assertThat(customer.identificationNumber(IdentificationType.NID)).contains("1990312456");
        assertThat(customer.identificationNumber(IdentificationType.PASSPORT)).isEmpty();
    }

    @Test
    void aDocumentIsUnverifiedUntilAnAuthoritySaysOtherwise() {
        CustomerIdentification nid =
                new CustomerIdentification(IdentificationType.NID, "1990312456");

        // Typed is not proved. This is the flag that keeps the two apart.
        assertThat(nid.isVerified()).isFalse();
        nid.markVerified(java.time.Instant.now());
        assertThat(nid.isVerified()).isTrue();
    }

    @Test
    void aDocumentWithNoExpiryNeverExpires() {
        CustomerIdentification tin =
                new CustomerIdentification(IdentificationType.TIN, "412563789012");

        assertThat(tin.isExpiredOn(TODAY.plusYears(50))).isFalse();
    }

    @Test
    void aDatedDocumentExpiresOnTheDayAfterItsExpiry() {
        CustomerIdentification passport =
                new CustomerIdentification(IdentificationType.PASSPORT, "BM0451236");
        passport.dateIt(LocalDate.of(2020, 6, 30), TODAY, "Dhaka");

        assertThat(passport.isExpiredOn(TODAY)).isFalse();
        assertThat(passport.isExpiredOn(TODAY.plusDays(1))).isTrue();
    }

    @Test
    void anAddressFormatsWithoutRepeatingACityThatIsAlsoTheDistrict() {
        CustomerAddress dhaka = new CustomerAddress(
                AddressType.PRESENT, "House 42, Road 11, Gulshan 1", "Dhaka", "Dhaka");
        CustomerAddress upcountry = new CustomerAddress(
                AddressType.PERMANENT, "Village Char Bhadrasan", "Faridpur", "Rajbari");

        assertThat(dhaka.formatted()).isEqualTo("House 42, Road 11, Gulshan 1, Dhaka");
        assertThat(upcountry.formatted()).isEqualTo("Village Char Bhadrasan, Faridpur, Rajbari");
    }

    private static Customer individual() {
        return new Customer("CIF-000001", CustomerType.INDIVIDUAL, "Rahim Uddin Ahmed", "01712345601");
    }
}
