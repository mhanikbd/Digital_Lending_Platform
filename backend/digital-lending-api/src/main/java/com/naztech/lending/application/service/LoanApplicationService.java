package com.naztech.lending.application.service;

import com.naztech.lending.application.domain.ApplicantType;
import com.naztech.lending.application.domain.ApplicationApplicant;
import com.naztech.lending.application.domain.ApplicationFinancial;
import com.naztech.lending.application.domain.LoanApplication;
import com.naztech.lending.application.domain.LoanPurpose;
import com.naztech.lending.application.domain.SourceChannel;
import com.naztech.lending.application.dto.ApplicationSummaryResponse;
import com.naztech.lending.application.dto.LoanApplicationDetailResponse;
import com.naztech.lending.application.dto.NewApplicationRequest;
import com.naztech.lending.application.repository.ApplicationCommentRepository;
import com.naztech.lending.application.repository.ApplicationQueryRepository;
import com.naztech.lending.application.repository.ApplicationStatusHistoryRepository;
import com.naztech.lending.application.repository.LoanApplicationRepository;
import com.naztech.lending.application.repository.LoanPurposeRepository;
import com.naztech.lending.auth.domain.RoleScope;
import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.domain.CustomerAddress;
import com.naztech.lending.customer.domain.IdentificationType;
import com.naztech.lending.customer.repository.CustomerRepository;
import com.naztech.lending.organization.service.OrganizationService;
import com.naztech.lending.pricing.dto.LoanQuote;
import com.naztech.lending.pricing.dto.LoanQuoteRequest;
import com.naztech.lending.pricing.service.PricingService;
import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.product.repository.LoanProductVersionRepository;
import com.naztech.lending.workflow.domain.WorkflowState;
import com.naztech.lending.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises and reads loan applications.
 *
 * <p>Creating one is the interesting half. An application is a snapshot: it
 * copies the applicant's details and finances as declared, records the product
 * version rather than the product, and stores the quotation rather than the
 * inputs to one. Everything that could be recomputed later is written down now,
 * because recomputing it later is exactly how a file quietly stops matching the
 * decision that was taken on it.
 *
 * <p>Reading is filtered by organisational scope, the same way the customer
 * master is. A branch officer sees their branch's files and nobody else's, and
 * an application outside that scope answers 404 rather than 403 - a 403 would
 * confirm the application number is real and merely held elsewhere.
 */
@Service
public class LoanApplicationService {

    /** Where a file begins. A row in the workflow, not a constant with meaning. */
    private static final String BRANCH_INITIAL_STATE = "SO_CREATED";
    private static final String FIELD_OFFICER_INITIAL_STATE = "FO_CREATED";

    private final LoanApplicationRepository applications;
    private final LoanPurposeRepository purposes;
    private final ApplicationStatusHistoryRepository history;
    private final ApplicationCommentRepository comments;
    private final ApplicationQueryRepository queries;
    private final CustomerRepository customers;
    private final LoanProductVersionRepository versions;
    private final OrganizationService organization;
    private final WorkflowService workflow;
    private final PricingService pricing;
    private final ApplicationAuditRecorder audit;
    private final Clock clock;

    public LoanApplicationService(LoanApplicationRepository applications,
                                  LoanPurposeRepository purposes,
                                  ApplicationStatusHistoryRepository history,
                                  ApplicationCommentRepository comments,
                                  ApplicationQueryRepository queries,
                                  CustomerRepository customers,
                                  LoanProductVersionRepository versions,
                                  OrganizationService organization,
                                  WorkflowService workflow,
                                  PricingService pricing,
                                  ApplicationAuditRecorder audit,
                                  Clock clock) {
        this.applications = applications;
        this.purposes = purposes;
        this.history = history;
        this.comments = comments;
        this.queries = queries;
        this.customers = customers;
        this.versions = versions;
        this.organization = organization;
        this.workflow = workflow;
        this.pricing = pricing;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Raises an application.
     *
     * <p>The quotation is produced here rather than trusted from the request. A
     * client may have shown the customer an indicative instalment; what gets
     * written on the file is the backend's, computed from the live product
     * version at the moment the file is raised.
     */
    @Transactional
    public LoanApplicationDetailResponse create(Actor actor, NewApplicationRequest request) {
        Customer customer = customers.findWithAddressesByCustomerId(request.customerId())
                .filter(candidate -> isVisibleTo(actor.userId(), candidate.getHomeBranch() == null
                        ? null : candidate.getHomeBranch().getId()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such customer"));
        customer.getIdentifications().size();

        LoanProductVersion version = versions.findActiveByProductCode(request.productCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No such product, or it is not currently on sale"));

        LoanPurpose purpose = purposes.findById(request.purposeCode())
                .filter(LoanPurpose::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_FAILED, "No such loan purpose"));
        if (purpose.isRequiresDetail()
                && (request.purposeDetail() == null || request.purposeDetail().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "%s applications must say more about the purpose".formatted(purpose.getName()));
        }

        SourceChannel channel = request.sourceChannel() == null
                ? SourceChannel.BRANCH : request.sourceChannel();
        if (channel.requiresFieldOfficer() && actor.userId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "A field officer application must be raised by a signed-in field officer");
        }

        // The quotation. Refused inputs surface as the pricing service's own
        // message, which names the bounds the product actually applies.
        LoanQuote quote = pricing.quote(new LoanQuoteRequest(
                request.productCode(), request.amount(), request.tenureMonths(), null), false);

        WorkflowState initial = workflow.initialState(
                        channel == SourceChannel.FIELD_OFFICER
                                ? FIELD_OFFICER_INITIAL_STATE : BRANCH_INITIAL_STATE)
                .orElseThrow(() -> new IllegalStateException(
                        "The workflow has no active initial state; V8 did not run"));

        Instant now = clock.instant();
        LoanApplication application = LoanApplication.raise(
                nextApplicationNo(now), customer, version, initial, channel,
                request.amount(), request.tenureMonths(), purpose.getCode(), actor.username());

        application.purposeDetail(request.purposeDetail());
        application.quotedAt(quote.instalment(), quote.totalPayable(), quote.netDisbursement());
        application.disburseTo(request.disbursementAccount());
        application.decidedBy(request.eligibilityId());
        if (channel.requiresFieldOfficer()) {
            application.raisedByFieldOfficer(actor.userId());
        }

        application.addApplicant(snapshotOf(customer));
        application.describeFinances(financialSnapshotOf(customer, request, quote));

        applications.save(application);

        audit.record(application.getId(), null, initial.getCode(),
                com.naztech.lending.workflow.domain.WorkflowAction.SUBMIT,
                actor, "Application raised", now);

        return detailOf(application);
    }

    /** Every application the caller may see, newest first. */
    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponse> list(UUID userId, String stateCode) {
        return visibleTo(userId, stateCode).stream()
                .map(ApplicationSummaryResponse::from)
                .toList();
    }

    /** One file in full, with its trail, its comments and its queries. */
    @Transactional(readOnly = true)
    public LoanApplicationDetailResponse detail(UUID userId, String applicationNo) {
        LoanApplication application = applications.findWithDetailByApplicationNo(applicationNo)
                .filter(candidate -> isVisibleTo(userId, branchIdOf(candidate)))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such application"));

        // Touched inside the transaction so they are loaded before the response
        // is assembled outside it.
        application.getApplicants().size();
        application.getDocuments().size();

        return detailOf(application);
    }

    /** The file, for a caller who has already been through the scope check. */
    @Transactional(readOnly = true)
    public LoanApplication require(UUID userId, String applicationNo) {
        return applications.findWithDetailByApplicationNo(applicationNo)
                .filter(candidate -> isVisibleTo(userId, branchIdOf(candidate)))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such application"));
    }

    @Transactional(readOnly = true)
    public List<LoanPurpose> activePurposes() {
        return purposes.findByStatusOrderByDisplayOrderAsc("ACTIVE");
    }

    private LoanApplicationDetailResponse detailOf(LoanApplication application) {
        return LoanApplicationDetailResponse.from(
                application,
                history.findByApplicationIdOrderByOccurredAtAsc(application.getId()),
                comments.findByApplicationIdOrderByCreatedAtAsc(application.getId()),
                queries.findByApplicationIdOrderByQueryNoAsc(application.getId()));
    }

    private List<LoanApplication> visibleTo(UUID userId, String stateCode) {
        boolean headOffice = organization.widestScopeOf(userId) == RoleScope.HEAD_OFFICE;

        if (headOffice) {
            return stateCode == null
                    ? applications.findAllByOrderByCreatedAtDesc()
                    : applications.findByStateCodeOrderByCreatedAtDesc(stateCode);
        }

        Set<UUID> branches = organization.visibleUnitIds(userId);
        if (branches.isEmpty()) {
            return List.of();
        }
        return stateCode == null
                ? applications.findByBranchIdInOrderByCreatedAtDesc(branches)
                : applications.findByStateCodeAndBranchIdInOrderByCreatedAtDesc(stateCode, branches);
    }

    /**
     * The same scope test the customer endpoints apply.
     *
     * <p>An application with no branch is head office's alone. That is the
     * honest reading: a file nobody has attached to a branch is not a file every
     * branch may see.
     */
    private boolean isVisibleTo(UUID userId, UUID branchId) {
        if (organization.widestScopeOf(userId) == RoleScope.HEAD_OFFICE) {
            return true;
        }
        if (branchId == null) {
            return false;
        }
        return organization.visibleUnitIds(userId).contains(branchId);
    }

    private static UUID branchIdOf(LoanApplication application) {
        return application.getBranch() == null ? null : application.getBranch().getId();
    }

    /**
     * The next application number for the year.
     *
     * <p>Readable on purpose - APP-2026-000042 is a number somebody can read
     * down a phone. Two applications raised in the same instant would compute
     * the same one; the unique constraint catches that, and the caller retries.
     */
    private String nextApplicationNo(Instant now) {
        int year = LocalDate.ofInstant(now, ZoneId.systemDefault()).getYear();
        String prefix = "APP-%d-".formatted(year);
        long next = applications.countWithPrefix(prefix) + 1;
        String candidate = "%s%06d".formatted(prefix, next);

        // A gap in the sequence - from a rolled-back attempt - would otherwise
        // make the count collide with a number already issued.
        while (applications.existsByApplicationNo(candidate)) {
            next++;
            candidate = "%s%06d".formatted(prefix, next);
        }
        return candidate;
    }

    /** The applicant as they are today, copied onto the file. */
    private ApplicationApplicant snapshotOf(Customer customer) {
        ApplicationApplicant applicant = new ApplicationApplicant(
                ApplicantType.PRIMARY, customer.getFullName(), customer.getMobile());

        applicant.describePerson(
                customer.getDateOfBirth(),
                customer.getGender() == null ? null : customer.getGender().name(),
                customer.getEmail(),
                customer.identificationNumber(IdentificationType.NID).orElse(null));

        applicant.describeWork(customer.getOccupation(), customer.getEmployerName(),
                customer.getDesignation());

        applicant.describeWhereTheyLive(
                addressOf(customer, com.naztech.lending.customer.domain.AddressType.PRESENT),
                addressOf(customer, com.naztech.lending.customer.domain.AddressType.PERMANENT));

        return applicant;
    }

    private static String addressOf(Customer customer,
                                    com.naztech.lending.customer.domain.AddressType type) {
        return customer.getAddresses().stream()
                .filter(address -> address.getAddressType() == type)
                .findFirst()
                .map(CustomerAddress::formatted)
                .orElse(null);
    }

    /**
     * The financial picture, taken from the customer record and overridden by
     * anything the application declares.
     *
     * <p>The debt burden ratio is computed once, here, against the instalment
     * that was actually quoted - so the ratio on the file is the one the
     * approver will see rather than one recalculated later from figures that
     * have moved.
     */
    private ApplicationFinancial financialSnapshotOf(Customer customer,
                                                     NewApplicationRequest request,
                                                     LoanQuote quote) {
        ApplicationFinancial financial = new ApplicationFinancial(
                request.monthlyIncome() != null ? request.monthlyIncome() : customer.getMonthlyIncome(),
                request.otherMonthlyIncome() != null
                        ? request.otherMonthlyIncome() : customer.getOtherMonthlyIncome(),
                request.monthlyExpense(),
                request.existingLiabilities() != null
                        ? request.existingLiabilities() : customer.getExistingLiabilities(),
                request.existingEmi());

        financial.describeSources(customer.getNetWorth(), customer.getSourceOfIncome(),
                customer.getSourceOfFunds());
        financial.computeDebtBurdenRatio(quote.instalment());
        return financial;
    }
}
