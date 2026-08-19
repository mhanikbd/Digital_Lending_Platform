package com.naztech.lending.application.domain;

import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.organization.domain.OrgUnit;
import com.naztech.lending.product.domain.LoanProduct;
import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.workflow.domain.WorkflowState;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The loan file.
 *
 * <p>An application is a <em>snapshot</em>, not a set of pointers. It records
 * the product version it was judged under, the rate it was quoted and the
 * instalment that was calculated - because a product repriced next month must
 * not silently change the basis of a decision already taken, and re-opening a
 * three-year-old file has to reproduce what was actually in front of the
 * approver.
 *
 * <p>It carries no method that decides whether a move is legal. Where the file
 * may go is the workflow engine's business, read from configuration; this class
 * only records where it went.
 */
@Entity
@Table(schema = "application", name = "t_loan_application")
public class LoanApplication {

    @Id
    private UUID id = UUID.randomUUID();

    /** What a branch quotes on the phone, and what the sanction letter carries. */
    @Column(name = "application_no", nullable = false, length = 30, updatable = false)
    private String applicationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private LoanProduct product;

    /** The terms. Never re-read from the product: that is the whole point. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_version_id", nullable = false, updatable = false)
    private LoanProductVersion productVersion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "state_code", nullable = false)
    private WorkflowState state;

    /** Which branch owns the file, so the queues filter as the customer list does. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private OrgUnit branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 30, updatable = false)
    private SourceChannel sourceChannel;

    @Column(name = "field_officer_id", updatable = false)
    private UUID fieldOfficerId;

    @Column(name = "requested_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "requested_tenure_months", nullable = false)
    private short requestedTenureMonths;

    @Column(name = "purpose_code", nullable = false, length = 30)
    private String purposeCode;

    @Column(name = "purpose_detail", length = 500)
    private String purposeDetail;

    /**
     * What was approved, once somebody has approved something.
     *
     * <p>Null until then, and never assumed equal to the requested amount: an
     * approver who cuts a loan from 50,000 to 30,000 has made a decision, and it
     * has to be visible as one.
     */
    @Column(name = "approved_amount", precision = 20, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "approved_tenure_months")
    private Short approvedTenureMonths;

    @Column(name = "interest_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "interest_method", nullable = false, length = 30)
    private String interestMethod;

    @Column(name = "instalment_amount", precision = 20, scale = 4)
    private BigDecimal instalmentAmount;

    @Column(name = "total_payable", precision = 20, scale = 4)
    private BigDecimal totalPayable;

    @Column(name = "net_disbursement", precision = 20, scale = 4)
    private BigDecimal netDisbursement;

    @Column(name = "disbursement_account", length = 34)
    private String disbursementAccount;

    /** The eligibility run that let this application exist. */
    @Column(name = "eligibility_id")
    private UUID eligibilityId;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "consent_ip", length = 45)
    private String consentIp;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ApplicationApplicant> applicants = new ArrayList<>();

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private ApplicationFinancial financial;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ApplicationDocument> documents = new ArrayList<>();

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

    protected LoanApplication() {
        // for JPA
    }

    /**
     * Raises a file.
     *
     * <p>Takes the product version rather than the product, and the quotation
     * rather than the inputs to one, because both are the things that must not
     * be recomputed later.
     */
    public static LoanApplication raise(String applicationNo, Customer customer,
                                        LoanProductVersion version, WorkflowState initialState,
                                        SourceChannel channel, BigDecimal requestedAmount,
                                        int tenureMonths, String purposeCode, String author) {
        LoanApplication application = new LoanApplication();
        application.applicationNo = applicationNo;
        application.customer = customer;
        application.product = version.getProduct();
        application.productVersion = version;
        application.state = initialState;
        application.branch = customer.getHomeBranch();
        application.sourceChannel = channel;
        application.requestedAmount = requestedAmount;
        application.requestedTenureMonths = (short) tenureMonths;
        application.purposeCode = purposeCode;
        application.interestRate = version.getInterestRate();
        application.interestMethod = version.getInterestMethod().name();
        application.createdBy = author;
        application.updatedBy = author;
        return application;
    }

    /** Records who raised it on the customer's behalf, when a channel needs one. */
    public void raisedByFieldOfficer(UUID fieldOfficerId) {
        this.fieldOfficerId = fieldOfficerId;
    }

    /** The quotation as it stood when the file was raised. */
    public void quotedAt(BigDecimal instalment, BigDecimal totalPayable,
                         BigDecimal netDisbursement) {
        this.instalmentAmount = instalment;
        this.totalPayable = totalPayable;
        this.netDisbursement = netDisbursement;
    }

    public void purposeDetail(String detail) {
        this.purposeDetail = detail;
    }

    public void disburseTo(String account) {
        this.disbursementAccount = account;
    }

    public void decidedBy(UUID eligibilityId) {
        this.eligibilityId = eligibilityId;
    }

    /**
     * Records the customer's consent.
     *
     * <p>Consent without a time is consent nobody can prove was given, so the
     * two are set together and the database refuses one without the other.
     */
    public void consented(Instant at, String ip) {
        this.consentGiven = true;
        this.consentAt = at;
        this.consentIp = ip;
    }

    /**
     * Moves the file.
     *
     * <p>Takes a state that the workflow engine has already decided is reachable.
     * Nothing here re-checks that, because a second opinion held in two places is
     * a second opinion that will eventually disagree with itself.
     */
    public void moveTo(WorkflowState next, Instant at, String author) {
        this.state = next;
        this.updatedAt = at;
        this.updatedBy = author;
        if (next.isTerminal()) {
            this.decidedAt = at;
        }
    }

    public void markSubmitted(Instant at) {
        this.submittedAt = at;
    }

    /** What an approver settled on, which may be less than what was asked for. */
    public void approvedFor(BigDecimal amount, Integer tenureMonths) {
        this.approvedAmount = amount;
        if (tenureMonths != null) {
            this.approvedTenureMonths = tenureMonths.shortValue();
        }
    }

    /** The amount the loan will actually be written for. */
    public BigDecimal effectiveAmount() {
        return approvedAmount != null ? approvedAmount : requestedAmount;
    }

    public int effectiveTenureMonths() {
        return approvedTenureMonths != null ? approvedTenureMonths : requestedTenureMonths;
    }

    public void addApplicant(ApplicationApplicant applicant) {
        applicant.attachTo(this);
        applicants.add(applicant);
    }

    public void describeFinances(ApplicationFinancial finances) {
        finances.attachTo(this);
        this.financial = finances;
    }

    public void addDocument(ApplicationDocument document) {
        document.attachTo(this);
        documents.add(document);
    }

    public UUID getId() {
        return id;
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public Customer getCustomer() {
        return customer;
    }

    public LoanProduct getProduct() {
        return product;
    }

    public LoanProductVersion getProductVersion() {
        return productVersion;
    }

    public WorkflowState getState() {
        return state;
    }

    public OrgUnit getBranch() {
        return branch;
    }

    public SourceChannel getSourceChannel() {
        return sourceChannel;
    }

    public UUID getFieldOfficerId() {
        return fieldOfficerId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public short getRequestedTenureMonths() {
        return requestedTenureMonths;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public String getPurposeDetail() {
        return purposeDetail;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public Short getApprovedTenureMonths() {
        return approvedTenureMonths;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public String getInterestMethod() {
        return interestMethod;
    }

    public BigDecimal getInstalmentAmount() {
        return instalmentAmount;
    }

    public BigDecimal getTotalPayable() {
        return totalPayable;
    }

    public BigDecimal getNetDisbursement() {
        return netDisbursement;
    }

    public String getDisbursementAccount() {
        return disbursementAccount;
    }

    public UUID getEligibilityId() {
        return eligibilityId;
    }

    public boolean isConsentGiven() {
        return consentGiven;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public List<ApplicationApplicant> getApplicants() {
        return applicants;
    }

    public ApplicationFinancial getFinancial() {
        return financial;
    }

    public List<ApplicationDocument> getDocuments() {
        return documents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
