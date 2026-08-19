package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One move the file has made.
 *
 * <p>Append only, and deliberately made of text rather than foreign keys. The
 * user who moved it may later be deleted and the state may later be retired; the
 * record of what happened must survive both. A history that can be emptied by a
 * tidy-up is not a history.
 *
 * <p>Both the user and their role are kept, because a person may hold two roles
 * and "which hat were they wearing" is exactly the question an audit asks.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_status_history")
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    /** Null on the first row: an application does not come from anywhere. */
    @Column(name = "from_state", length = 40, updatable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 40, updatable = false)
    private String toState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private com.naztech.lending.workflow.domain.WorkflowAction action;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_username", nullable = false, length = 64, updatable = false)
    private String actorUsername;

    @Column(name = "actor_role", length = 40, updatable = false)
    private String actorRole;

    @Column(length = 1000, updatable = false)
    private String reason;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected ApplicationStatusHistory() {
        // for JPA
    }

    public ApplicationStatusHistory(UUID applicationId, String fromState, String toState,
                                    com.naztech.lending.workflow.domain.WorkflowAction action,
                                    UUID actorUserId, String actorUsername, String actorRole,
                                    String reason, String correlationId, Instant occurredAt) {
        this.applicationId = applicationId;
        this.fromState = fromState;
        this.toState = toState;
        this.action = action;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.reason = truncate(reason, 1000);
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    /** The column is bounded; an over-long reason must not lose the whole row. */
    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    public Long getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public com.naztech.lending.workflow.domain.WorkflowAction getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
