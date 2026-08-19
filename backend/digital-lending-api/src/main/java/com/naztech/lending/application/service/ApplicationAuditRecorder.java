package com.naztech.lending.application.service;

import com.naztech.lending.application.domain.ApplicationStatusHistory;
import com.naztech.lending.application.repository.ApplicationStatusHistoryRepository;
import com.naztech.lending.common.correlation.CorrelationId;
import com.naztech.lending.workflow.domain.WorkflowAction;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the trail of what happened to an application.
 *
 * <p>Unlike the rule engine's recorder, this one runs <em>inside</em> the
 * caller's transaction, and that difference is deliberate. A rule evaluation is
 * a read that must be recorded even when the request it belongs to fails; a
 * workflow move is a write, and a history row for a move that rolled back would
 * be a trail describing something that never happened.
 *
 * <p>So the rule is: the file moves and the history is written, or neither is.
 */
@Component
public class ApplicationAuditRecorder {

    private final ApplicationStatusHistoryRepository history;

    public ApplicationAuditRecorder(ApplicationStatusHistoryRepository history) {
        this.history = history;
    }

    /**
     * Records one move.
     *
     * @param actorRole the role the workflow accepted the action under, which is
     *                  not necessarily the first role the person happens to hold
     */
    @Transactional
    public void record(UUID applicationId, String fromState, String toState, WorkflowAction action,
                       Actor actor, String actorRole, String reason, Instant at) {
        history.save(new ApplicationStatusHistory(
                applicationId, fromState, toState, action,
                actor.userId(), actor.username(), actorRole,
                reason, CorrelationId.current(), at));
    }

    /** The same, when no single role owns the action. */
    @Transactional
    public void record(UUID applicationId, String fromState, String toState, WorkflowAction action,
                       Actor actor, String reason, Instant at) {
        record(applicationId, fromState, toState, action, actor, actor.primaryRole(), reason, at);
    }
}
