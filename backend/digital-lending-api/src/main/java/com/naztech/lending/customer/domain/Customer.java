package com.naztech.lending.customer.domain;

import com.naztech.lending.organization.domain.OrgUnit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One person or business the bank holds a relationship with.
 *
 * <p>Every monetary field is a {@link BigDecimal} against a {@code NUMERIC(20,4)}
 * column. Never a double: a rounding error in a declared income becomes a
 * rounding error in the limit it justifies, and the limit is what the bank
 * lends against.
 *
 * <p>The customer belongs to a branch, which is what gives the organisational
 * scope rules something to filter. A branch-scoped officer reads the customers
 * of the branches they are posted to, and no others.
 */
@Entity
@Table(schema = "customer", name = "t_customer")
public class Customer {

    @Id
    private UUID id = UUID.randomUUID();

    /** The number a branch quotes on the phone; a business identifier. */
    @Column(name = "customer_id", nullable = false, length = 20, updatable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_branch_id")
    private OrgUnit homeBranch;

    /** Null for a customer who has a record but has never used the app. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "father_name", length = 160)
    private String fatherName;

    @Column(name = "mother_name", length = 160)
    private String motherName;

    @Column(name = "spouse_name", length = 160)
    private String spouseName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(nullable = false, length = 60)
    private String nationality = "Bangladeshi";

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    /** Free text: banks disagree on the categories, and none of them branch on it. */
    @Column(name = "education_level", length = 40)
    private String educationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "residence_status", nullable = false, length = 20)
    private ResidenceStatus residenceStatus = ResidenceStatus.RESIDENT;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(length = 160)
    private String email;

    @Column(length = 80)
    private String occupation;

    @Column(length = 80)
    private String designation;

    @Column(name = "employer_name", length = 160)
    private String employerName;

    @Column(name = "monthly_income", precision = 20, scale = 4)
    private BigDecimal monthlyIncome;

    @Column(name = "other_monthly_income", precision = 20, scale = 4)
    private BigDecimal otherMonthlyIncome;

    @Column(name = "source_of_income", length = 120)
    private String sourceOfIncome;

    @Column(name = "source_of_funds", length = 120)
    private String sourceOfFunds;

    @Column(name = "net_worth", precision = 20, scale = 4)
    private BigDecimal netWorth;

    @Column(name = "existing_liabilities", precision = 20, scale = 4)
    private BigDecimal existingLiabilities;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", nullable = false, length = 20)
    private RiskProfile riskProfile = RiskProfile.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "onboarded_on", nullable = false)
    private LocalDate onboardedOn = LocalDate.now();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<CustomerAddress> addresses = new LinkedHashSet<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<CustomerIdentification> identifications = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "system";

    @Version
    @Column(nullable = false)
    private long version;

    protected Customer() {
        // for JPA
    }

    public Customer(String customerId, CustomerType customerType, String fullName, String mobile) {
        this.customerId = customerId;
        this.customerType = customerType;
        this.fullName = fullName;
        this.mobile = mobile;
    }

    /**
     * Total declared monthly income.
     *
     * <p>Both parts are optional and either may be absent, so this answers zero
     * rather than null: a caller adding income to an expense has no use for a
     * null, and every one of them would otherwise write the same guard.
     */
    public BigDecimal totalMonthlyIncome() {
        BigDecimal primary = monthlyIncome == null ? BigDecimal.ZERO : monthlyIncome;
        BigDecimal other = otherMonthlyIncome == null ? BigDecimal.ZERO : otherMonthlyIncome;
        return primary.add(other);
    }

    /**
     * Age in whole years on the given day, when a date of birth is held.
     *
     * <p>Eligibility rules are written against age, and computing it from the
     * date of birth each time is the only way it stays right the day after a
     * birthday.
     */
    public Optional<Integer> ageOn(LocalDate day) {
        return Optional.ofNullable(dateOfBirth)
                .map(birth -> java.time.Period.between(birth, day).getYears());
    }

    /** True when this customer may be considered for lending at all. */
    public boolean isEligibleToBorrow() {
        return status.canTransact() && !customerType.requiresGuardian();
    }

    public void addAddress(CustomerAddress address) {
        addresses.add(address);
        address.attachTo(this);
    }

    public void addIdentification(CustomerIdentification identification) {
        identifications.add(identification);
        identification.attachTo(this);
    }

    /** The number of a document of the given kind, when the customer holds one. */
    public Optional<String> identificationNumber(IdentificationType type) {
        return identifications.stream()
                .filter(document -> document.getIdType() == type)
                .map(CustomerIdentification::getIdNumber)
                .findFirst();
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public OrgUnit getHomeBranch() {
        return homeBranch;
    }

    public void setHomeBranch(OrgUnit homeBranch) {
        this.homeBranch = homeBranch;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getFatherName() {
        return fatherName;
    }

    public String getMotherName() {
        return motherName;
    }

    public String getSpouseName() {
        return spouseName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public String getNationality() {
        return nationality;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public String getEducationLevel() {
        return educationLevel;
    }

    public ResidenceStatus getResidenceStatus() {
        return residenceStatus;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getDesignation() {
        return designation;
    }

    public String getEmployerName() {
        return employerName;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getOtherMonthlyIncome() {
        return otherMonthlyIncome;
    }

    public String getSourceOfIncome() {
        return sourceOfIncome;
    }

    public String getSourceOfFunds() {
        return sourceOfFunds;
    }

    public BigDecimal getNetWorth() {
        return netWorth;
    }

    public BigDecimal getExistingLiabilities() {
        return existingLiabilities;
    }

    public String getCurrency() {
        return currency;
    }

    public RiskProfile getRiskProfile() {
        return riskProfile;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public LocalDate getOnboardedOn() {
        return onboardedOn;
    }

    public Set<CustomerAddress> getAddresses() {
        return addresses;
    }

    public Set<CustomerIdentification> getIdentifications() {
        return identifications;
    }

    /** Everything a branch types in. Grouped so the constructor stays readable. */
    public void describePerson(String fatherName, String motherName, String spouseName,
                               LocalDate dateOfBirth, Gender gender, MaritalStatus maritalStatus,
                               String educationLevel) {
        this.fatherName = fatherName;
        this.motherName = motherName;
        this.spouseName = spouseName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.maritalStatus = maritalStatus;
        this.educationLevel = educationLevel;
    }

    public void describeOccupation(String occupation, String designation, String employerName) {
        this.occupation = occupation;
        this.designation = designation;
        this.employerName = employerName;
    }

    public void describeFinances(BigDecimal monthlyIncome, BigDecimal otherMonthlyIncome,
                                 String sourceOfIncome, String sourceOfFunds,
                                 BigDecimal netWorth, BigDecimal existingLiabilities) {
        this.monthlyIncome = monthlyIncome;
        this.otherMonthlyIncome = otherMonthlyIncome;
        this.sourceOfIncome = sourceOfIncome;
        this.sourceOfFunds = sourceOfFunds;
        this.netWorth = netWorth;
        this.existingLiabilities = existingLiabilities;
    }

    public void describeStanding(RiskProfile riskProfile, KycStatus kycStatus, String email) {
        this.riskProfile = riskProfile;
        this.kycStatus = kycStatus;
        this.email = email;
    }
}
