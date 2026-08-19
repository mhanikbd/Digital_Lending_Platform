package com.naztech.lending.rules.domain;

import com.naztech.lending.product.domain.LoanProductVersion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A set of rules and the way they combine.
 *
 * <p>A group tied to a product version is evaluated only for that version, so
 * repricing can also change who qualifies. A group with no version applies to
 * every product, which is where bank-wide screening lives.
 */
@Entity
@Table(schema = "rules", name = "t_rule_group")
public class RuleGroup {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_version_id")
    private LoanProductVersion productVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RulePurpose purpose = RulePurpose.ELIGIBILITY;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_operator", nullable = false, length = 5)
    private LogicalOperator logicalOperator = LogicalOperator.AND;

    /** Lower runs first, so the cheap decisive checks can be put in front. */
    @Column(nullable = false)
    private short priority = 100;

    /**
     * What the customer is told when the group fails. Written for a person, and
     * deliberately not assembled from the rules, which are written for a machine.
     */
    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleStatus status = RuleStatus.ACTIVE;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("priority ASC")
    private List<Rule> rules = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "system";

    @Version
    @Column(nullable = false)
    private long version;

    protected RuleGroup() {
        // for JPA
    }

    /**
     * Builds a group.
     *
     * <p>A group with no product version applies bank-wide; one tied to a
     * version is evaluated only for that version, which is how repricing can
     * also change who qualifies.
     */
    public static RuleGroup of(String code, String name, LogicalOperator logicalOperator,
                               LoanProductVersion productVersion) {
        RuleGroup group = new RuleGroup();
        group.code = code;
        group.name = name;
        group.logicalOperator = logicalOperator;
        group.productVersion = productVersion;
        return group;
    }

    /** What the customer is told when the whole group fails. */
    public RuleGroup sayingOnFailure(String message) {
        this.failureMessage = message;
        return this;
    }

    /** Lower runs first, so decisive checks can be put in front. */
    public RuleGroup atPriority(int priority) {
        this.priority = (short) priority;
        return this;
    }

    /** Adds a rule, setting both sides of the association. */
    public RuleGroup with(Rule rule) {
        rules.add(rule);
        return this;
    }

    public boolean isActive() {
        return status == RuleStatus.ACTIVE;
    }

    /** The rules that actually take part, in the order they were given. */
    public List<Rule> activeRules() {
        return rules.stream().filter(Rule::isActive).toList();
    }

    /** What is shown when the group fails, falling back to the group's name. */
    public String messageOnFailure() {
        return failureMessage != null ? failureMessage : name + " not satisfied";
    }

    public UUID getId() {
        return id;
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

    public LoanProductVersion getProductVersion() {
        return productVersion;
    }

    public RulePurpose getPurpose() {
        return purpose;
    }

    public LogicalOperator getLogicalOperator() {
        return logicalOperator;
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

    public List<Rule> getRules() {
        return rules;
    }
}
