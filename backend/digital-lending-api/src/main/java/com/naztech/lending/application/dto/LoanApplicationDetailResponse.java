package com.naztech.lending.application.dto;

import com.naztech.lending.application.domain.ApplicationApplicant;
import com.naztech.lending.application.domain.ApplicationComment;
import com.naztech.lending.application.domain.ApplicationQuery;
import com.naztech.lending.application.domain.ApplicationQueryResponse;
import com.naztech.lending.application.domain.ApplicationStatusHistory;
import com.naztech.lending.application.domain.LoanApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The whole file.
 *
 * <p>Everything an officer needs to explain a decision: the terms it was judged
 * under, the applicant as they were declared, the finances the ratio was
 * computed from, every move it has made and everything anybody said about it.
 *
 * <p>Every decimal is a JSON string. Parse with a decimal library.
 */
@Schema(description = "A loan application in full")
public record LoanApplicationDetailResponse(

        @Schema(example = "APP-2026-000042") String applicationNo,
        @Schema(example = "SO_CREATED") String stateCode,
        @Schema(example = "With the sourcing officer") String stateName,
        @Schema(example = "1") int stepNo,
        @Schema(example = "Origination") String stepName,
        @Schema(example = "IN_PROGRESS") String customerStage,

        @Schema(example = "CIF-000001") String customerId,
        @Schema(example = "BR-101") String branchCode,
        @Schema(example = "FIELD_OFFICER") String sourceChannel,

        @Schema(example = "ELOAN") String productCode,
        @Schema(example = "e-Loan") String productName,
        @Schema(description = "The version the file was judged under", example = "1")
        int productVersion,
        @Schema(example = "BDT") String currency,

        @Schema(example = "35000.0000") BigDecimal requestedAmount,
        @Schema(example = "12") int requestedTenureMonths,
        BigDecimal approvedAmount,
        Integer approvedTenureMonths,

        @Schema(example = "MEDICAL") String purposeCode,
        String purposeDetail,

        @Schema(description = "Percent per annum", example = "9.000000") BigDecimal interestRate,
        @Schema(example = "REDUCING_BALANCE") String interestMethod,
        @Schema(description = "As quoted when the file was raised", example = "3060.80")
        BigDecimal instalmentAmount,
        @Schema(example = "37307.12") BigDecimal totalPayable,
        @Schema(description = "What reaches the account after fees taken at disbursement",
                example = "34422.50") BigDecimal netDisbursement,
        String disbursementAccount,

        boolean consentGiven,
        Instant consentAt,
        Instant submittedAt,
        Instant decidedAt,
        Instant createdAt,

        List<ApplicantView> applicants,
        FinancialView financial,
        List<HistoryView> history,
        List<CommentView> comments,
        List<QueryView> queries) {

    public static LoanApplicationDetailResponse from(LoanApplication application,
                                                     List<ApplicationStatusHistory> history,
                                                     List<ApplicationComment> comments,
                                                     List<ApplicationQuery> queries) {
        return new LoanApplicationDetailResponse(
                application.getApplicationNo(),
                application.getState().getCode(),
                application.getState().getName(),
                application.getState().getStepNo(),
                application.getState().getStepName(),
                application.getState().getCustomerStage().name(),
                application.getCustomer().getCustomerId(),
                application.getBranch() == null ? null : application.getBranch().getCode(),
                application.getSourceChannel().name(),
                application.getProduct().getCode(),
                application.getProduct().getName(),
                application.getProductVersion().getVersionNo(),
                application.getProductVersion().getCurrency(),
                application.getRequestedAmount(),
                application.getRequestedTenureMonths(),
                application.getApprovedAmount(),
                application.getApprovedTenureMonths() == null
                        ? null : (int) application.getApprovedTenureMonths(),
                application.getPurposeCode(),
                application.getPurposeDetail(),
                application.getInterestRate(),
                application.getInterestMethod(),
                application.getInstalmentAmount(),
                application.getTotalPayable(),
                application.getNetDisbursement(),
                application.getDisbursementAccount(),
                application.isConsentGiven(),
                application.getConsentAt(),
                application.getSubmittedAt(),
                application.getDecidedAt(),
                application.getCreatedAt(),
                application.getApplicants().stream().map(ApplicantView::from).toList(),
                FinancialView.from(application),
                history.stream().map(HistoryView::from).toList(),
                comments.stream().map(CommentView::from).toList(),
                queries.stream().map(QueryView::from).toList());
    }

    /** The applicant as declared on the day, not as the customer record reads now. */
    @Schema(description = "An applicant, as declared when the file was raised")
    public record ApplicantView(String applicantType, String fullName, LocalDate dateOfBirth,
                                String gender, String mobile, String email, String nationalId,
                                String occupation, String employerName, String designation,
                                String presentAddress, String permanentAddress) {

        static ApplicantView from(ApplicationApplicant applicant) {
            return new ApplicantView(
                    applicant.getApplicantType().name(), applicant.getFullName(),
                    applicant.getDateOfBirth(), applicant.getGender(), applicant.getMobile(),
                    applicant.getEmail(), applicant.getNationalId(), applicant.getOccupation(),
                    applicant.getEmployerName(), applicant.getDesignation(),
                    applicant.getPresentAddress(), applicant.getPermanentAddress());
        }
    }

    @Schema(description = "The finances the decision was taken on")
    public record FinancialView(BigDecimal monthlyIncome, BigDecimal otherMonthlyIncome,
                                BigDecimal monthlyExpense, BigDecimal existingLiabilities,
                                BigDecimal existingEmi, BigDecimal netWorth,
                                @Schema(description = "Fraction of income committed to debt "
                                        + "if this loan is written", example = "0.183000")
                                BigDecimal debtBurdenRatio) {

        static FinancialView from(LoanApplication application) {
            var financial = application.getFinancial();
            if (financial == null) {
                return null;
            }
            return new FinancialView(
                    financial.getMonthlyIncome(), financial.getOtherMonthlyIncome(),
                    financial.getMonthlyExpense(), financial.getExistingLiabilities(),
                    financial.getExistingEmi(), financial.getNetWorth(),
                    financial.getDebtBurdenRatio());
        }
    }

    @Schema(description = "One move the file has made")
    public record HistoryView(String fromState, String toState, String action,
                              String actorUsername, String actorRole, String reason,
                              Instant occurredAt) {

        static HistoryView from(ApplicationStatusHistory row) {
            return new HistoryView(row.getFromState(), row.getToState(), row.getAction().name(),
                    row.getActorUsername(), row.getActorRole(), row.getReason(),
                    row.getOccurredAt());
        }
    }

    @Schema(description = "A note somebody made")
    public record CommentView(String stateCode, String authorUsername, String authorRole,
                              String comment, boolean internalOnly, Instant createdAt) {

        static CommentView from(ApplicationComment comment) {
            return new CommentView(comment.getStateCode(), comment.getAuthorUsername(),
                    comment.getAuthorRole(), comment.getComment(), comment.isInternalOnly(),
                    comment.getCreatedAt());
        }
    }

    /** A query and its answers. Both are kept: §23 requires it. */
    @Schema(description = "A credit query and everything said in answer to it")
    public record QueryView(int queryNo, String question, String queryType, String status,
                            String raisedBy, String raisedByRole, Instant raisedAt,
                            List<ResponseView> responses) {

        static QueryView from(ApplicationQuery query) {
            return new QueryView(query.getQueryNo(), query.getQuestion(),
                    query.getQueryType().name(), query.getStatus().name(), query.getRaisedBy(),
                    query.getRaisedByRole(), query.getRaisedAt(),
                    query.getResponses().stream().map(ResponseView::from).toList());
        }
    }

    @Schema(description = "One answer to a query")
    public record ResponseView(String response, String respondedBy, String respondedByRole,
                               Instant respondedAt) {

        static ResponseView from(ApplicationQueryResponse response) {
            return new ResponseView(response.getResponse(), response.getRespondedBy(),
                    response.getRespondedByRole(), response.getRespondedAt());
        }
    }
}
