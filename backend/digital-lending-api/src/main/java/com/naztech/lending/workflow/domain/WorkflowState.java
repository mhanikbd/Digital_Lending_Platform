package com.naztech.lending.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One state of the six-step workflow.
 *
 * <p>A row rather than an enum, because the specification requires a bank to be
 * able to change the workflow without a release. Nothing in Java names a state.
 */
@Entity
@Table(schema = "workflow", name = "t_workflow_state")
public class WorkflowState {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "step_no", nullable = false)
    private short stepNo;

    @Column(name = "step_name", nullable = false, length = 60)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_type", nullable = false, length = 20)
    private StateType stateType = StateType.INTERMEDIATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_stage", nullable = false, length = 40)
    private CustomerStage customerStage = CustomerStage.IN_PROGRESS;

    /** Null means no service level applies, which is not the same as zero. */
    @Column(name = "sla_hours")
    private Short slaHours;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WorkflowState() {
        // for JPA
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** True when the application has finished and no action will be offered. */
    public boolean isTerminal() {
        return stateType == StateType.TERMINAL;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public short getStepNo() {
        return stepNo;
    }

    public String getStepName() {
        return stepName;
    }

    public StateType getStateType() {
        return stateType;
    }

    public CustomerStage getCustomerStage() {
        return customerStage;
    }

    public Short getSlaHours() {
        return slaHours;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public String getStatus() {
        return status;
    }
}
