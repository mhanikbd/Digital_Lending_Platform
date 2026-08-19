package com.naztech.lending.rules.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A fact that may be tested.
 *
 * <p>The catalogue exists so a screen can offer a list instead of a free text
 * box, and so that a misspelt attribute name is refused when the rule is saved
 * rather than quietly evaluating to false when somebody's application is being
 * decided.
 */
@Entity
@Table(schema = "rules", name = "t_rule_attribute")
public class RuleAttribute {

    @Id
    @Column(length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private RuleDataType dataType;

    /** Which module supplies the value when a context is assembled. */
    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RuleAttribute() {
        // for JPA
    }

    /**
     * Declares a testable fact.
     *
     * <p>The catalogue is seeded by migration today and will be extended by the
     * configuration screens of Milestone 21. Construction is public because
     * both of those are legitimate authors of it, and because a rule cannot be
     * built or tested without an attribute to test.
     */
    public static RuleAttribute of(String code, String name, String description,
                                   RuleDataType dataType, String source) {
        RuleAttribute attribute = new RuleAttribute();
        attribute.code = code;
        attribute.name = name;
        attribute.description = description;
        attribute.dataType = dataType;
        attribute.source = source;
        return attribute;
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

    public RuleDataType getDataType() {
        return dataType;
    }

    public String getSource() {
        return source;
    }
}
