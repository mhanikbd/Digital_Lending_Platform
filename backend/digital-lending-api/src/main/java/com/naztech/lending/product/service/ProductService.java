package com.naztech.lending.product.service;

import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.product.domain.LoanProduct;
import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.product.domain.RepaymentFrequency;
import com.naztech.lending.product.domain.VersionStatus;
import com.naztech.lending.product.dto.NewProductRequest;
import com.naztech.lending.product.dto.ProductDetailResponse;
import com.naztech.lending.product.dto.ProductSummaryResponse;
import com.naztech.lending.product.dto.ProductVersionResponse;
import com.naztech.lending.product.dto.VersionAmendmentRequest;
import com.naztech.lending.product.repository.LoanProductRepository;
import com.naztech.lending.product.repository.LoanProductVersionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The product catalogue, and the versioning that keeps it honest.
 *
 * <p>Milestone 13 is the catalogue; Milestone 14 is the rule that its terms are
 * never edited once they are being lent against. Both live here because they are
 * the same rule seen twice: a product is what the bank sells, a version is what
 * it sold on a given day, and an application must be able to point at the second
 * for the whole life of the loan.
 *
 * <p>So there is no method that changes an active version. Repricing drafts a
 * copy, the copy is amended, and activating it retires the incumbent in the same
 * transaction.
 */
@Service
public class ProductService {

    private final LoanProductRepository products;
    private final LoanProductVersionRepository versions;
    private final Clock clock;

    public ProductService(LoanProductRepository products, LoanProductVersionRepository versions,
                          Clock clock) {
        this.products = products;
        this.versions = versions;
        this.clock = clock;
    }

    /**
     * The catalogue, each product showing whichever version is on sale today.
     *
     * <p>The live version comes with its fees and risk ceilings. That costs a
     * couple of extra selects per product, and it is worth them: a catalogue
     * that shows a rate but not the processing fee shows the customer less than
     * half of what the loan costs, and the alternative is every client fetching
     * each product separately to find out.
     *
     * <p>Bounded on purpose - only the live version is loaded, never the
     * history. A product repriced fifty times still costs the same two selects.
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> list() {
        LocalDate today = LocalDate.now(clock);
        List<LoanProduct> catalogue = products.findAllByOrderByCodeAsc();

        catalogue.forEach(product -> product.sellableVersionOn(today).ifPresent(version -> {
            version.getFees().size();
            version.getRiskLimits().size();
            version.offeredTenures();
        }));

        return catalogue.stream()
                .map(product -> ProductSummaryResponse.from(product, today))
                .toList();
    }

    /** One product with every version it has ever had, including retired ones. */
    @Transactional(readOnly = true)
    public ProductDetailResponse detail(String code) {
        LoanProduct product = products.findWithVersionsByCode(code)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such product"));

        // Touched inside the transaction so the collections are loaded before
        // the response is assembled outside it. Separate fetches rather than one
        // entity graph: joining three collections multiplies the rows together.
        product.getVersions().forEach(version -> {
            version.getFees().size();
            version.getRiskLimits().size();
            version.offeredTenures();
        });

        return ProductDetailResponse.from(product, LocalDate.now(clock));
    }

    /**
     * Registers a product and drafts the first version of its terms.
     *
     * <p>The version is a draft. Registering a product and putting it on sale
     * are separate decisions, and the second is a separate call - which also
     * means a half-configured product cannot be sold by accident.
     */
    @Transactional
    public ProductVersionResponse create(NewProductRequest request, String author) {
        if (products.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "A product with code %s already exists".formatted(request.code()));
        }
        if (request.maxAmount().compareTo(request.minAmount()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The maximum amount cannot be below the minimum");
        }

        Set<Short> tenures = new LinkedHashSet<>();
        for (Integer months : request.tenures()) {
            if (months == null || months <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "A tenure must be a positive number of months");
            }
            tenures.add(months.shortValue());
        }

        RepaymentFrequency frequency = request.repaymentFrequency() == null
                ? RepaymentFrequency.MONTHLY : request.repaymentFrequency();
        for (Short months : tenures) {
            if (months % frequency.monthsPerPeriod() != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        ("A tenure of %d months is not a whole number of %s periods")
                                .formatted(months, frequency.name().toLowerCase()));
            }
        }

        LoanProduct product = LoanProduct.of(request.code(), request.name(), request.nameBn(),
                request.productType(), request.category(), request.description(), author);

        LoanProductVersion version = LoanProductVersion.initial(product,
                request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now(clock),
                request.currency() != null ? request.currency() : "BDT",
                request.minAmount(), request.maxAmount(), tenures,
                request.interestMethod(), request.interestRate(), frequency, author);

        version.withLimits(request.incomeMultiple(), request.maxDbr(),
                request.regulatoryMaxAmount(), request.recommendedRatio(),
                request.maxTotalExposure());

        product.with(version);
        products.save(product);

        return ProductVersionResponse.full(versions.save(version));
    }

    /**
     * Drafts the next version of a product.
     *
     * <p>Copied from whichever version is live, or from the newest one when
     * nothing is - a product whose only version was retired can still be
     * repriced back into existence.
     *
     * <p>The draft is not on sale. It has to be activated, which is a separate
     * decision and a separate call.
     */
    @Transactional
    public ProductVersionResponse draftNextVersion(String code, VersionAmendmentRequest amendment,
                                                   String author) {
        LoanProduct product = products.findWithVersionsByCode(code)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such product"));

        versions.findByProductIdAndStatus(product.getId(), VersionStatus.DRAFT)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            ("Version %d is already a draft. Activate or amend it rather than "
                                    + "starting another.").formatted(existing.getVersionNo()));
                });

        LoanProductVersion source = product.getVersions().stream()
                .filter(candidate -> candidate.getStatus() == VersionStatus.ACTIVE)
                .findFirst()
                .or(() -> product.getVersions().stream()
                        .max(java.util.Comparator.comparingInt(LoanProductVersion::getVersionNo)))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "This product has no version to copy from"));

        // Loaded before the copy, or the new version inherits empty collections.
        source.getFees().size();
        source.getRiskLimits().size();
        source.offeredTenures();

        int nextNumber = versions.findHighestVersionNo(product.getId()).orElse(0) + 1;
        LocalDate from = amendment.effectiveFrom() != null
                ? amendment.effectiveFrom() : LocalDate.now(clock);

        LoanProductVersion draft = LoanProductVersion.draftFrom(source, nextNumber, from, author);
        apply(draft, amendment, author);

        return ProductVersionResponse.full(versions.save(draft));
    }

    /** Amends a draft that has not been activated. */
    @Transactional
    public ProductVersionResponse amendDraft(String code, int versionNo,
                                             VersionAmendmentRequest amendment, String author) {
        LoanProductVersion version = versionOf(code, versionNo);
        version.getFees().size();
        version.getRiskLimits().size();
        apply(version, amendment, author);
        return ProductVersionResponse.full(versions.save(version));
    }

    /**
     * Puts a draft on sale, retiring whatever it replaces.
     *
     * <p>One transaction, and the order matters: the incumbent is retired first,
     * because the database permits only one active version per product and would
     * otherwise refuse the second write. Doing both together is also what makes
     * it impossible for the product to be briefly unsellable.
     */
    @Transactional
    public ProductVersionResponse activate(String code, int versionNo, String author) {
        LoanProductVersion draft = versionOf(code, versionNo);
        LocalDate today = LocalDate.now(clock);

        versions.findByProductIdAndStatus(draft.getProduct().getId(), VersionStatus.ACTIVE)
                .ifPresent(incumbent -> {
                    incumbent.retire(today, author);
                    versions.saveAndFlush(incumbent);
                });

        try {
            draft.activate(today, author);
        } catch (IllegalStateException notADraft) {
            throw new BusinessException(ErrorCode.CONFLICT, notADraft.getMessage());
        }

        draft.getFees().size();
        draft.getRiskLimits().size();
        return ProductVersionResponse.full(versions.save(draft));
    }

    /**
     * Takes a version off sale without replacing it.
     *
     * <p>Leaves the product with nothing on sale, which is the honest state for
     * a product that has been withdrawn. Eligibility and quotation both answer
     * "not currently on sale" rather than falling back to older terms.
     */
    @Transactional
    public ProductVersionResponse retire(String code, int versionNo, String author) {
        LoanProductVersion version = versionOf(code, versionNo);
        try {
            version.retire(LocalDate.now(clock), author);
        } catch (IllegalStateException notActive) {
            throw new BusinessException(ErrorCode.CONFLICT, notActive.getMessage());
        }
        version.getFees().size();
        version.getRiskLimits().size();
        return ProductVersionResponse.full(versions.save(version));
    }

    private LoanProductVersion versionOf(String code, int versionNo) {
        LoanProduct product = products.findByCode(code)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such product"));
        return versions.findByProductIdAndVersionNo(product.getId(), versionNo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such version of this product"));
    }

    /**
     * Applies an amendment, refusing anything that would leave the version
     * incoherent.
     *
     * <p>The database carries the same checks. They are repeated here so the
     * caller gets a sentence naming the field rather than a constraint violation
     * naming a constraint.
     */
    private void apply(LoanProductVersion version, VersionAmendmentRequest amendment,
                       String author) {
        Set<Short> tenures = null;
        if (amendment.tenures() != null && !amendment.tenures().isEmpty()) {
            tenures = new LinkedHashSet<>();
            for (Integer months : amendment.tenures()) {
                if (months == null || months <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "A tenure must be a positive number of months");
                }
                tenures.add(months.shortValue());
            }
        }

        try {
            version.amend(amendment.minAmount(), amendment.maxAmount(),
                    amendment.interestMethod(), amendment.interestRate(),
                    amendment.incomeMultiple(), amendment.maxDbr(),
                    amendment.regulatoryMaxAmount(), amendment.recommendedRatio(),
                    amendment.maxTotalExposure(), tenures, author);
        } catch (IllegalStateException notEditable) {
            throw new BusinessException(ErrorCode.CONFLICT, notEditable.getMessage());
        }

        if (version.getMaxAmount().compareTo(version.getMinAmount()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The maximum amount cannot be below the minimum");
        }
    }
}
