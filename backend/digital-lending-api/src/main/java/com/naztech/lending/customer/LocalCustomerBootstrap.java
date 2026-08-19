package com.naztech.lending.customer;

import com.naztech.lending.customer.domain.AddressType;
import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.domain.CustomerAddress;
import com.naztech.lending.customer.domain.CustomerIdentification;
import com.naztech.lending.customer.domain.CustomerType;
import com.naztech.lending.customer.domain.Gender;
import com.naztech.lending.customer.domain.IdentificationType;
import com.naztech.lending.customer.domain.KycStatus;
import com.naztech.lending.customer.domain.MaritalStatus;
import com.naztech.lending.customer.domain.ResidenceStatus;
import com.naztech.lending.customer.domain.RiskProfile;
import com.naztech.lending.customer.repository.CustomerRepository;
import com.naztech.lending.organization.domain.OrgUnit;
import com.naztech.lending.organization.repository.OrgUnitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ten existing customers on a developer machine.
 *
 * <p>Existing in the sense the specification uses in its customer journey: people
 * who already bank here, hold an account at a branch, and can therefore be found
 * rather than onboarded. They are spread across all four seeded branches on
 * purpose, so the organisational scope rules have something to be visibly right
 * or wrong about - a Gulshan officer should see three of these and not the other
 * seven.
 *
 * <p>Local profile only, and for the same reason as the seeded administrator and
 * the seeded hierarchy: a migration runs everywhere, and invented people reaching
 * a real bank's customer master would be far worse than useless.
 *
 * <p>Ordered after the organisation, because every one of them is attached to a
 * branch that runner creates.
 */
@Component
@Profile("local")
@Order(30)
public class LocalCustomerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalCustomerBootstrap.class);

    private final CustomerRepository customers;
    private final OrgUnitRepository units;

    public LocalCustomerBootstrap(CustomerRepository customers, OrgUnitRepository units) {
        this.customers = customers;
        this.units = units;
    }

    /** One seeded customer, flattened so the list below reads as a table. */
    private record Seed(
            String customerId, String fullName, CustomerType type, Gender gender,
            String branchCode, LocalDate dateOfBirth, MaritalStatus marital,
            String fatherName, String motherName, String spouseName,
            String occupation, String designation, String employer,
            String mobile, String email,
            long monthlyIncome, long otherIncome, long netWorth, long liabilities,
            String sourceOfIncome, String sourceOfFunds,
            KycStatus kyc, RiskProfile risk, ResidenceStatus residence,
            String nid, String tin, String passport,
            String presentLine, String presentCity, String presentDistrict,
            String permanentLine, String permanentCity, String permanentDistrict) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("CIF-000001", "Rahim Uddin Ahmed", CustomerType.INDIVIDUAL, Gender.MALE,
                    "BR-101", LocalDate.of(1988, 3, 14), MaritalStatus.MARRIED,
                    "Kabir Uddin Ahmed", "Rahima Khatun", "Sultana Rahim",
                    "Service", "Senior Software Engineer", "Brain Station 23",
                    "01712345601", "rahim.ahmed@example.com",
                    120000, 15000, 4500000, 850000, "Salary", "Employment",
                    KycStatus.VERIFIED, RiskProfile.LOW, ResidenceStatus.RESIDENT,
                    "1990312456", "412563789012", "BM0451236",
                    "House 42, Road 11, Gulshan 1", "Dhaka", "Dhaka",
                    "Village Char Bhadrasan", "Faridpur", "Faridpur"),

            new Seed("CIF-000002", "Fatema Begum", CustomerType.INDIVIDUAL, Gender.FEMALE,
                    "BR-101", LocalDate.of(1985, 7, 2), MaritalStatus.MARRIED,
                    "Abdul Karim", "Hasina Begum", "Mizanur Rahman",
                    "Service", "Consultant Physician", "Square Hospitals Ltd",
                    "01712345602", "fatema.begum@example.com",
                    185000, 0, 9200000, 1200000, "Professional practice", "Employment",
                    KycStatus.VERIFIED, RiskProfile.LOW, ResidenceStatus.RESIDENT,
                    "1990312457", "412563789013", null,
                    "Apartment 5B, Road 27, Banani", "Dhaka", "Dhaka",
                    "Apartment 5B, Road 27, Banani", "Dhaka", "Dhaka"),

            new Seed("CIF-000003", "Kamrul Hasan", CustomerType.SOLE_PROPRIETOR, Gender.MALE,
                    "BR-102", LocalDate.of(1979, 11, 23), MaritalStatus.MARRIED,
                    "Nurul Hasan", "Ayesha Siddika", "Rehana Hasan",
                    "Business", "Proprietor", "Hasan Trading",
                    "01712345603", "kamrul.hasan@example.com",
                    260000, 40000, 15800000, 4300000, "Trading business", "Business income",
                    KycStatus.VERIFIED, RiskProfile.MEDIUM, ResidenceStatus.RESIDENT,
                    "1990312458", "412563789014", "BM0451240",
                    "Shop 12, Banani Super Market", "Dhaka", "Dhaka",
                    "Village Baniachang", "Habiganj", "Habiganj"),

            new Seed("CIF-000004", "Nasrin Akter", CustomerType.INDIVIDUAL, Gender.FEMALE,
                    "BR-102", LocalDate.of(1992, 1, 30), MaritalStatus.SINGLE,
                    "Mokbul Hossain", "Shirin Akter", null,
                    "Service", "Senior Teacher", "Viqarunnisa Noon School",
                    "01712345604", "nasrin.akter@example.com",
                    55000, 0, 1350000, 220000, "Salary", "Employment",
                    KycStatus.VERIFIED, RiskProfile.LOW, ResidenceStatus.RESIDENT,
                    "1990312459", "412563789015", null,
                    "Flat 3A, Road 5, Banani DOHS", "Dhaka", "Dhaka",
                    "Village Kaliakair", "Gazipur", "Gazipur"),

            new Seed("CIF-000005", "Shahidul Islam", CustomerType.INDIVIDUAL, Gender.MALE,
                    "BR-201", LocalDate.of(1983, 5, 18), MaritalStatus.MARRIED,
                    "Rafiqul Islam", "Momena Begum", "Rokeya Sultana",
                    "Service", "Assistant Vice President", "Prime Bank PLC",
                    "01712345605", "shahidul.islam@example.com",
                    98000, 12000, 5600000, 1850000, "Salary", "Employment",
                    KycStatus.VERIFIED, RiskProfile.MEDIUM, ResidenceStatus.RESIDENT,
                    "1990312460", "412563789016", null,
                    "House 9, Arambagh", "Dhaka", "Dhaka",
                    "Village Ullapara", "Sirajganj", "Sirajganj"),

            new Seed("CIF-000006", "Rubina Khatun", CustomerType.INDIVIDUAL, Gender.FEMALE,
                    "BR-201", LocalDate.of(1995, 9, 9), MaritalStatus.MARRIED,
                    "Siddique Mia", "Jahanara Begum", "Alamgir Hossain",
                    "Service", "Production Supervisor", "Ha-Meem Group",
                    "01712345606", null,
                    42000, 0, 480000, 160000, "Salary", "Employment",
                    KycStatus.IN_PROGRESS, RiskProfile.MEDIUM, ResidenceStatus.RESIDENT,
                    "1990312461", null, null,
                    "House 22, Fakirapool", "Dhaka", "Dhaka",
                    "Village Bhairab Bazar", "Kishoreganj", "Kishoreganj"),

            new Seed("CIF-000007", "Anwar Hossain", CustomerType.INDIVIDUAL, Gender.MALE,
                    "BR-301", LocalDate.of(1976, 2, 27), MaritalStatus.MARRIED,
                    "Nazir Ahmed", "Salma Khatun", "Nargis Anwar",
                    "Business", "Managing Partner", "Chittagong Shipping Lines",
                    "01712345607", "anwar.hossain@example.com",
                    145000, 55000, 21500000, 7600000, "Shipping agency", "Business income",
                    KycStatus.VERIFIED, RiskProfile.MEDIUM, ResidenceStatus.RESIDENT,
                    "1990312462", "412563789017", "BM0451255",
                    "Plot 17, Agrabad Commercial Area", "Chattogram", "Chattogram",
                    "Village Patiya", "Chattogram", "Chattogram"),

            new Seed("CIF-000008", "Tahmina Sultana", CustomerType.INDIVIDUAL, Gender.FEMALE,
                    "BR-301", LocalDate.of(1990, 12, 5), MaritalStatus.SINGLE,
                    "Golam Mostafa", "Ruksana Begum", null,
                    "Service", "Chief Pharmacist", "Chattogram Medical Centre",
                    "01712345608", "tahmina.sultana@example.com",
                    68000, 0, 1750000, 0, "Salary", "Employment",
                    KycStatus.PENDING, RiskProfile.MEDIUM, ResidenceStatus.RESIDENT,
                    "1990312463", null, null,
                    "House 4, Nasirabad Housing Society", "Chattogram", "Chattogram",
                    "House 4, Nasirabad Housing Society", "Chattogram", "Chattogram"),

            new Seed("CIF-000009", "Jahangir Alam", CustomerType.INDIVIDUAL, Gender.MALE,
                    "BR-101", LocalDate.of(1961, 8, 16), MaritalStatus.MARRIED,
                    "Late Abdul Mannan", "Late Sufia Khatun", "Shamsun Nahar",
                    "Retired", "Retired Deputy Secretary", "Ministry of Finance",
                    "01712345609", "jahangir.alam@example.com",
                    46000, 22000, 8900000, 0, "Pension", "Retirement benefits",
                    KycStatus.VERIFIED, RiskProfile.LOW, ResidenceStatus.RESIDENT,
                    "1990312464", "412563789018", null,
                    "House 88, Road 4, Niketan", "Dhaka", "Dhaka",
                    "Village Shibpur", "Narsingdi", "Narsingdi"),

            new Seed("CIF-000010", "Sadia Rahman", CustomerType.INDIVIDUAL, Gender.FEMALE,
                    "BR-102", LocalDate.of(1993, 4, 21), MaritalStatus.SINGLE,
                    "Habibur Rahman", "Nilufar Yasmin", null,
                    "Service", "Product Designer", "Careem, Dubai",
                    "01712345610", "sadia.rahman@example.com",
                    210000, 0, 3100000, 640000, "Overseas salary", "Employment abroad",
                    KycStatus.IN_PROGRESS, RiskProfile.HIGH, ResidenceStatus.NON_RESIDENT,
                    "1990312465", "412563789019", "BM0451261",
                    "Marina Residences 4, Dubai Marina", "Dubai", null,
                    "House 31, Road 8, Dhanmondi", "Dhaka", "Dhaka"));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (customers.count() > 0) {
            return;
        }
        SEEDS.forEach(this::create);
        log.info("Seeded {} existing customers across the local branches", SEEDS.size());
    }

    private void create(Seed seed) {
        OrgUnit branch = units.findByCode(seed.branchCode()).orElse(null);
        if (branch == null) {
            log.warn("Branch {} is missing, so {} was not seeded", seed.branchCode(), seed.customerId());
            return;
        }

        Customer customer = new Customer(
                seed.customerId(), seed.type(), seed.fullName(), seed.mobile());
        customer.setHomeBranch(branch);
        customer.describePerson(seed.fatherName(), seed.motherName(), seed.spouseName(),
                seed.dateOfBirth(), seed.gender(), seed.marital(), "Graduate");
        customer.describeOccupation(seed.occupation(), seed.designation(), seed.employer());
        customer.describeFinances(
                taka(seed.monthlyIncome()), taka(seed.otherIncome()),
                seed.sourceOfIncome(), seed.sourceOfFunds(),
                taka(seed.netWorth()), taka(seed.liabilities()));
        customer.describeStanding(seed.risk(), seed.kyc(), seed.email());

        customer.addAddress(new CustomerAddress(AddressType.PRESENT,
                seed.presentLine(), seed.presentCity(), seed.presentDistrict()));
        customer.addAddress(new CustomerAddress(AddressType.PERMANENT,
                seed.permanentLine(), seed.permanentCity(), seed.permanentDistrict()));

        // A verified customer has had their documents actually checked. The rest
        // hold the same numbers unverified, which is the state KYC starts from.
        boolean checked = seed.kyc() == KycStatus.VERIFIED;
        addDocument(customer, IdentificationType.NID, seed.nid(), checked, null);
        addDocument(customer, IdentificationType.TIN, seed.tin(), checked, null);
        addDocument(customer, IdentificationType.PASSPORT, seed.passport(), checked,
                LocalDate.of(2030, 6, 30));

        customers.save(customer);
    }

    private static void addDocument(Customer customer, IdentificationType type, String number,
                                    boolean verified, LocalDate expiry) {
        if (number == null) {
            return;
        }
        CustomerIdentification document = new CustomerIdentification(type, number);
        if (expiry != null) {
            document.dateIt(expiry.minusYears(10), expiry, "Dhaka");
        }
        if (verified) {
            document.markVerified(Instant.now());
        }
        customer.addIdentification(document);
    }

    /** Whole taka, at the scale the NUMERIC(20,4) column holds. */
    private static BigDecimal taka(long amount) {
        return BigDecimal.valueOf(amount).setScale(4);
    }
}
