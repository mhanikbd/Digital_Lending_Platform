package com.naztech.lending.rules.domain;

/**
 * How a rule tests its attribute.
 *
 * <p>{@code IN} and {@code NOT_IN} read the comparison value as a comma
 * separated list; {@code BETWEEN} reads it as the lower bound and takes the
 * upper from the rule's second value. Every other operator uses a single token.
 */
public enum RuleOperator {

    EQ("is"),
    NEQ("is not"),
    GT("is greater than"),
    GTE("is at least"),
    LT("is less than"),
    LTE("is at most"),
    IN("is one of"),
    NOT_IN("is none of"),
    BETWEEN("is between");

    private final String phrase;

    RuleOperator(String phrase) {
        this.phrase = phrase;
    }

    /** Reads as English in a decline reason, which is where these end up. */
    public String phrase() {
        return phrase;
    }

    public boolean isList() {
        return this == IN || this == NOT_IN;
    }

    public boolean isRange() {
        return this == BETWEEN;
    }
}
