package com.naztech.lending.rules.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * What kind of fact an attribute holds, which decides how the stored comparison
 * text is read and which operators may be applied to it.
 *
 * <p>Values are held as text in one column rather than four typed columns that
 * are mostly null. The cost of that is parsing, and the parsing lives here so it
 * happens the same way everywhere.
 */
public enum RuleDataType {

    NUMBER(Set.of(RuleOperator.EQ, RuleOperator.NEQ, RuleOperator.GT, RuleOperator.GTE,
            RuleOperator.LT, RuleOperator.LTE, RuleOperator.BETWEEN,
            RuleOperator.IN, RuleOperator.NOT_IN)),

    /** Ordering two names is meaningless, so strings compare only for identity. */
    STRING(Set.of(RuleOperator.EQ, RuleOperator.NEQ, RuleOperator.IN, RuleOperator.NOT_IN)),

    BOOLEAN(Set.of(RuleOperator.EQ, RuleOperator.NEQ)),

    DATE(Set.of(RuleOperator.EQ, RuleOperator.NEQ, RuleOperator.GT, RuleOperator.GTE,
            RuleOperator.LT, RuleOperator.LTE, RuleOperator.BETWEEN));

    private final Set<RuleOperator> operators;

    RuleDataType(Set<RuleOperator> operators) {
        this.operators = operators;
    }

    public boolean supports(RuleOperator operator) {
        return operators.contains(operator);
    }

    public Set<RuleOperator> supportedOperators() {
        return operators;
    }

    /**
     * Reads one token into something comparable.
     *
     * <p>Returns null for text this type cannot read rather than throwing: a
     * misconfigured rule must fail the applicant's check with a recorded reason,
     * not abort the whole evaluation and take the other five rules with it.
     */
    public Comparable<?> parse(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim();
        try {
            return switch (this) {
                case NUMBER -> new BigDecimal(token);
                case STRING -> token;
                case BOOLEAN -> parseBoolean(token);
                case DATE -> LocalDate.parse(token);
            };
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    private Boolean parseBoolean(String token) {
        if (token.equalsIgnoreCase("true") || token.equalsIgnoreCase("yes")) {
            return Boolean.TRUE;
        }
        if (token.equalsIgnoreCase("false") || token.equalsIgnoreCase("no")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Not a boolean: " + token);
    }
}
