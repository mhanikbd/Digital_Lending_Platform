package com.naztech.lending.rules.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * What a rule engine run decided, kept.
 *
 * <p>Append only. A customer who was declined is entitled to know why years
 * later, and the bank is required to be able to say - so the record carries the
 * attribute values as they were at the time. The customer's income changes; the
 * reason they were declined must not change with it.
 */
@Entity
@Table(schema = "rules", name = "t_rule_evaluation")
public class RuleEvaluation {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "subject_type", nullable = false, length = 20, updatable = false)
    private String subjectType = "CUSTOMER";

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    /** The exact version evaluated, without which the record cannot be read back. */
    @Column(name = "product_version_id", updatable = false)
    private UUID productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private EvaluationOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", nullable = false, updatable = false)
    private String contextSnapshot;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt = Instant.now();

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RuleEvaluationDetail> details = new ArrayList<>();

    protected RuleEvaluation() {
        // for JPA
    }

    public RuleEvaluation(UUID subjectId, UUID productVersionId, EvaluationOutcome outcome,
                          String contextSnapshot, String correlationId, Instant evaluatedAt) {
        this.subjectId = subjectId;
        this.productVersionId = productVersionId;
        this.outcome = outcome;
        this.contextSnapshot = contextSnapshot;
        this.correlationId = correlationId;
        this.evaluatedAt = evaluatedAt;
    }

    /** Attaches one line of the reasoning, both sides of the association at once. */
    public void add(RuleEvaluationDetail detail) {
        detail.attachTo(this);
        details.add(detail);
    }

    public UUID getId() {
        return id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public EvaluationOutcome getOutcome() {
        return outcome;
    }

    public String getContextSnapshot() {
        return contextSnapshot;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public List<RuleEvaluationDetail> getDetails() {
        return details;
    }
}
