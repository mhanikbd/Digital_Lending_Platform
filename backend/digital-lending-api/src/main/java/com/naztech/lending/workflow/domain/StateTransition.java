package com.naztech.lending.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A legal move: from one state, by one action, to another.
 *
 * <p>The specification calls this the recommend/return map. It is named for what
 * it holds instead, because the seeds already need six actions and a table
 * called "recommend_return" containing an ESCALATE row is a table nobody trusts.
 */
@Entity
@Table(schema = "workflow", name = "t_state_transition")
public class StateTransition {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_state", nullable = false, updatable = false)
    private WorkflowState fromState;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_state", nullable = false, updatable = false)
    private WorkflowState toState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowAction action;

    /**
     * Which role this move belongs to, when several roles take the same action
     * from the same state and land somewhere different.
     *
     * <p>Null means the move is open to whoever the role/state map allows, which
     * is almost all of them. The branch recommendation is the case that forces
     * the column to exist: three roles RECOMMEND from the same state into three
     * different destinations, and the file has to record which of them did it.
     */
    @Column(name = "actor_role_code", length = 40)
    private String actorRoleCode;

    /**
     * What the button says. Held here so the same word appears on the screen, in
     * the history and in the notification, rather than three teams each choosing
     * their own.
     */
    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "reason_required", nullable = false)
    private boolean reasonRequired;

    @Column(name = "display_order", nullable = false)
    private short displayOrder = 100;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected StateTransition() {
        // for JPA
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public UUID getId() {
        return id;
    }

    public WorkflowState getFromState() {
        return fromState;
    }

    public WorkflowState getToState() {
        return toState;
    }

    public WorkflowAction getAction() {
        return action;
    }

    public String getActorRoleCode() {
        return actorRoleCode;
    }

    /**
     * True when a person holding these roles may take this particular move.
     *
     * <p>An untagged move is open to anyone the role/state map has already
     * allowed; a tagged one belongs to its role alone.
     */
    public boolean isOpenTo(java.util.Collection<String> roleCodes) {
        return actorRoleCode == null || roleCodes.contains(actorRoleCode);
    }

    public String getLabel() {
        return label;
    }

    public boolean isReasonRequired() {
        return reasonRequired;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public String getStatus() {
        return status;
    }
}
