package com.naztech.lending.rules.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One rule, and what it said.
 *
 * <p>Codes rather than foreign keys, deliberately. A rule may be edited or
 * deleted after the decision was made, and the record of the decision has to
 * survive that intact - a reason that changes when somebody retunes the
 * criteria is not a reason at all.
 */
@Entity
@Table(schema = "rules", name = "t_rule_evaluation_detail")
public class RuleEvaluationDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false, updatable = false)
    private RuleEvaluation evaluation;

    @Column(name = "group_code", nullable = false, length = 40, updatable = false)
    private String groupCode;

    @Column(name = "attribute_code", nullable = false, length = 60, updatable = false)
    private String attributeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private RuleOperator operator;

    @Column(name = "expected_value", nullable = false, length = 255, updatable = false)
    private String expectedValue;

    /** Null when the context could not supply the attribute at all. */
    @Column(name = "actual_value", length = 255, updatable = false)
    private String actualValue;

    @Column(nullable = false, updatable = false)
    private boolean passed;

    @Column(length = 255, updatable = false)
    private String message;

    protected RuleEvaluationDetail() {
        // for JPA
    }

    public RuleEvaluationDetail(String groupCode, String attributeCode, RuleOperator operator,
                                String expectedValue, String actualValue, boolean passed,
                                String message) {
        this.groupCode = groupCode;
        this.attributeCode = attributeCode;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.actualValue = truncate(actualValue);
        this.passed = passed;
        this.message = truncate(message);
    }

    void attachTo(RuleEvaluation owner) {
        this.evaluation = owner;
    }

    /** The columns are 255; an over-long value must not lose the whole record. */
    private static String truncate(String value) {
        if (value == null || value.length() <= 255) {
            return value;
        }
        return value.substring(0, 255);
    }

    public Long getId() {
        return id;
    }

    public RuleEvaluation getEvaluation() {
        return evaluation;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public String getAttributeCode() {
        return attributeCode;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}
