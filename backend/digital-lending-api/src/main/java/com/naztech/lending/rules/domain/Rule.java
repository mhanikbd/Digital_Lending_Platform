package com.naztech.lending.rules.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One test: an attribute, an operator, and a value to compare against.
 *
 * <p>The comparison value is text and is read according to the attribute's
 * declared type. That keeps one column instead of four mostly-null typed ones,
 * at the cost of a parse - and the parse is where a misconfiguration shows up,
 * which is why it reports rather than throws.
 */
@Entity
@Table(schema = "rules", name = "t_rule")
public class Rule {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private RuleGroup group;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "attribute_code", nullable = false)
    private RuleAttribute attribute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RuleOperator operator;

    @Column(name = "comparison_value", nullable = false, length = 255)
    private String comparisonValue;

    @Column(name = "comparison_value2", length = 255)
    private String comparisonValue2;

    /** Inverts this rule alone, which is the specification's NOT. */
    @Column(nullable = false)
    private boolean negate;

    @Column(nullable = false)
    private short priority = 100;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleStatus status = RuleStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected Rule() {
        // for JPA
    }

    /**
     * Builds a rule.
     *
     * <p>The comparison values are text on purpose - see the class note. A
     * BETWEEN carries both bounds; every other operator leaves the second null,
     * and the database refuses the combinations that make no sense.
     */
    public static Rule of(RuleGroup group, RuleAttribute attribute, RuleOperator operator,
                          String comparisonValue, String comparisonValue2) {
        Rule rule = new Rule();
        rule.group = group;
        rule.attribute = attribute;
        rule.operator = operator;
        rule.comparisonValue = comparisonValue;
        rule.comparisonValue2 = comparisonValue2;
        return rule;
    }

    /** The message shown when this rule declines somebody. */
    public Rule sayingOnFailure(String message) {
        this.failureMessage = message;
        return this;
    }

    /** Inverts the rule, which is the specification's NOT applied to one test. */
    public Rule negated() {
        this.negate = true;
        return this;
    }

    /** Lower runs first within a group. */
    public Rule atPriority(int priority) {
        this.priority = (short) priority;
        return this;
    }

    /** Takes the rule out of evaluation without deleting the history behind it. */
    public Rule deactivated() {
        this.status = RuleStatus.INACTIVE;
        return this;
    }

    public boolean isActive() {
        return status == RuleStatus.ACTIVE;
    }

    /** The tokens of an IN or NOT_IN list, trimmed and with blanks dropped. */
    public List<String> comparisonTokens() {
        return Arrays.stream(comparisonValue.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    /**
     * How this rule reads to a person, for the audit detail and for an
     * administrator reviewing a configuration they did not write.
     */
    public String describe() {
        String subject = attribute.getName();
        String predicate = switch (operator) {
            case BETWEEN -> "%s %s and %s".formatted(
                    operator.phrase(), comparisonValue, comparisonValue2);
            case IN, NOT_IN -> "%s %s".formatted(
                    operator.phrase(), String.join(", ", comparisonTokens()));
            default -> "%s %s".formatted(operator.phrase(), comparisonValue);
        };
        return negate
                ? "NOT (%s %s)".formatted(subject, predicate)
                : "%s %s".formatted(subject, predicate);
    }

    /** What is shown when this rule fails, falling back to its own description. */
    public String messageOnFailure() {
        return failureMessage != null ? failureMessage : describe();
    }

    /** The expected value as one string, for the immutable evaluation record. */
    public String expectedValue() {
        return operator.isRange()
                ? comparisonValue + " - " + comparisonValue2
                : comparisonValue;
    }

    public UUID getId() {
        return id;
    }

    public RuleGroup getGroup() {
        return group;
    }

    public RuleAttribute getAttribute() {
        return attribute;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public String getComparisonValue() {
        return comparisonValue;
    }

    public String getComparisonValue2() {
        return comparisonValue2;
    }

    public boolean isNegate() {
        return negate;
    }

    public short getPriority() {
        return priority;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public RuleStatus getStatus() {
        return status;
    }
}
