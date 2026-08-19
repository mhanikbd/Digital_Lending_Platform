package com.naztech.lending.application.service;

import com.naztech.lending.application.domain.ApplicationComment;
import com.naztech.lending.application.domain.ApplicationQuery;
import com.naztech.lending.application.domain.ApplicationQueryResponse;
import com.naztech.lending.application.domain.LoanApplication;
import com.naztech.lending.application.domain.QueryStatus;
import com.naztech.lending.application.domain.QueryType;
import com.naztech.lending.application.dto.ActionRequest;
import com.naztech.lending.application.dto.AvailableActionResponse;
import com.naztech.lending.application.dto.LoanApplicationDetailResponse;
import com.naztech.lending.application.repository.ApplicationCommentRepository;
import com.naztech.lending.application.repository.ApplicationQueryRepository;
import com.naztech.lending.application.repository.ApplicationStatusHistoryRepository;
import com.naztech.lending.application.repository.LoanApplicationRepository;
import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.workflow.domain.StateTransition;
import com.naztech.lending.workflow.domain.WorkflowAction;
import com.naztech.lending.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves an application through the workflow.
 *
 * <p>The division of labour is the point of the whole milestone.
 * {@link WorkflowService} decides whether a move is legal, reading configuration
 * and knowing nothing about loans. This class knows what a loan application is
 * and does the things a move implies - stamping the submission time, recording
 * the approved amount, opening and closing queries - and asks the engine for
 * permission rather than deciding for itself.
 *
 * <p>Nothing here names a role or a state. Search this file for {@code "BM"} and
 * you will not find it, which is the test the specification actually sets.
 */
@Service
public class ApplicationWorkflowService {

    private final LoanApplicationRepository applications;
    private final ApplicationCommentRepository comments;
    private final ApplicationQueryRepository queries;
    private final ApplicationStatusHistoryRepository history;
    private final LoanApplicationService loanApplications;
    private final WorkflowService workflow;
    private final ApplicationAuditRecorder audit;
    private final Clock clock;

    public ApplicationWorkflowService(LoanApplicationRepository applications,
                                      ApplicationCommentRepository comments,
                                      ApplicationQueryRepository queries,
                                      ApplicationStatusHistoryRepository history,
                                      LoanApplicationService loanApplications,
                                      WorkflowService workflow,
                                      ApplicationAuditRecorder audit,
                                      Clock clock) {
        this.applications = applications;
        this.comments = comments;
        this.queries = queries;
        this.history = history;
        this.loanApplications = loanApplications;
        this.workflow = workflow;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * What this person may do to this file, right now.
     *
     * <p>The endpoint §24 asks for by name. A screen calls it and draws what it
     * is told, instead of working out for itself which buttons a branch manager
     * should see - which is the same hard-coding the specification forbids in
     * the backend, merely moved somewhere harder to audit.
     */
    @Transactional(readOnly = true)
    public List<AvailableActionResponse> availableActions(Actor actor, String applicationNo) {
        LoanApplication application = loanApplications.require(actor.userId(), applicationNo);
        return workflow.availableActions(application.getState().getCode(), actor.roles()).stream()
                .map(AvailableActionResponse::from)
                .toList();
    }

    /**
     * Takes an action on a file.
     *
     * <p>Four things are checked, in this order: that the caller may see the
     * file at all, that the workflow permits the action from where the file is,
     * that a reason was given when the transition demands one, and only then is
     * anything written. The order matters - refusing for a missing reason before
     * checking authority would tell somebody which actions exist on a file they
     * may not touch.
     */
    @Transactional
    public LoanApplicationDetailResponse act(Actor actor, String applicationNo,
                                             ActionRequest request) {
        LoanApplication application = loanApplications.require(actor.userId(), applicationNo);
        String fromState = application.getState().getCode();

        if (application.getState().isTerminal()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "This application is %s and accepts no further action"
                            .formatted(application.getState().getName()));
        }

        StateTransition transition = workflow.resolve(
                fromState, request.action(), actor.roles(), request.toState());

        if (transition.isReasonRequired()
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "%s requires a reason".formatted(transition.getLabel()));
        }

        Instant now = clock.instant();
        applyTo(application, transition, request, actor, now);

        application.moveTo(transition.getToState(), now, actor.username());
        applications.save(application);

        audit.record(application.getId(), fromState, transition.getToState().getCode(),
                transition.getAction(), actor, roleFor(transition, actor),
                request.reason(), now);

        return loanApplications.detail(actor.userId(), applicationNo);
    }

    /**
     * The things a particular move implies, beyond changing the state.
     *
     * <p>A switch on the action rather than on the state, deliberately. Actions
     * are a fixed vocabulary the database constrains; states are configuration a
     * bank may extend. Branching on the state would break the moment somebody
     * added one.
     */
    private void applyTo(LoanApplication application, StateTransition transition,
                         ActionRequest request, Actor actor, Instant now) {
        switch (transition.getAction()) {
            case SUBMIT -> {
                if (application.getSubmittedAt() == null) {
                    application.markSubmitted(now);
                }
                answerOpenQuery(application, request, actor, now);
            }
            case APPROVE, APPROVE_WITH_CONDITION -> {
                // An approver may cut the amount. Absent means they took it as
                // asked, which is recorded as such rather than left null.
                application.approvedFor(
                        request.approvedAmount() != null
                                ? request.approvedAmount() : application.getRequestedAmount(),
                        request.approvedTenureMonths() != null
                                ? request.approvedTenureMonths()
                                : (int) application.getRequestedTenureMonths());
            }
            case QUERY -> raiseQuery(application, request, actor, now);
            default -> {
                // RECOMMEND, RETURN, REJECT, ALLOCATE, ESCALATE, DISBURSE and
                // CANCEL change where the file is and nothing else about it. The
                // reason travels on the history row, which every action writes.
            }
        }

        if (request.comment() != null && !request.comment().isBlank()) {
            comments.save(new ApplicationComment(
                    application.getId(), application.getState().getCode(), actor.userId(),
                    actor.username(), roleFor(transition, actor), request.comment(), true, now));
        }
    }

    private void raiseQuery(LoanApplication application, ActionRequest request, Actor actor,
                            Instant now) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "A query needs a question");
        }
        int next = (int) queries.countByApplicationId(application.getId()) + 1;
        queries.save(new ApplicationQuery(
                application.getId(), next, request.reason(),
                request.queryType() == null ? QueryType.INFORMATION : request.queryType(),
                actor.userId(), actor.username(), actor.primaryRole(), now));
    }

    /**
     * Answers the oldest open query, when the move is the one that answers it.
     *
     * <p>Silent when there is none. A SUBMIT that happens to occur while no
     * query is open is an ordinary save, not an error - and the state machine
     * has already decided the move is legal.
     */
    private void answerOpenQuery(LoanApplication application, ActionRequest request, Actor actor,
                                 Instant now) {
        if (request.reason() == null || request.reason().isBlank()) {
            return;
        }
        queries.findFirstByApplicationIdAndStatusOrderByQueryNoAsc(
                        application.getId(), QueryStatus.OPEN)
                .ifPresent(query -> {
                    query.answer(new ApplicationQueryResponse(
                            request.reason(), actor.userId(), actor.username(),
                            actor.primaryRole(), now));
                    queries.save(query);
                });
    }

    /**
     * The role to record the action under.
     *
     * <p>When the transition names one, that is the answer - the branch
     * recommendation belongs to whichever of the three roles made it. Otherwise
     * the person's first role, which is right whenever they hold only one and
     * honest about the ambiguity when they hold several.
     */
    private String roleFor(StateTransition transition, Actor actor) {
        return transition.getActorRoleCode() != null
                ? transition.getActorRoleCode() : actor.primaryRole();
    }

    /** Adds a note without moving the file. */
    @Transactional
    public LoanApplicationDetailResponse comment(Actor actor, String applicationNo, String text,
                                                 boolean internalOnly) {
        LoanApplication application = loanApplications.require(actor.userId(), applicationNo);

        // Commenting needs the same authority as looking, and no more. Somebody
        // who may read a file may say something about it.
        if (!workflow.permits(application.getState().getCode(), WorkflowAction.VIEW, actor.roles())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Your role cannot act on this application where it currently sits");
        }

        comments.save(new ApplicationComment(
                application.getId(), application.getState().getCode(), actor.userId(),
                actor.username(), actor.primaryRole(), text, internalOnly, clock.instant()));

        return loanApplications.detail(actor.userId(), applicationNo);
    }

    /** Every move the file has made, for the audit tab. */
    @Transactional(readOnly = true)
    public List<com.naztech.lending.application.domain.ApplicationStatusHistory> trail(
            UUID userId, String applicationNo) {
        LoanApplication application = loanApplications.require(userId, applicationNo);
        return history.findByApplicationIdOrderByOccurredAtAsc(application.getId());
    }
}
