package com.naztech.lending.rules.service;

import com.naztech.lending.rules.domain.Rule;
import com.naztech.lending.rules.domain.RuleDataType;
import com.naztech.lending.rules.domain.RuleOperator;
import java.math.BigDecimal;
import java.util.List;

/**
 * Decides whether one rule holds.
 *
 * <p>No Spring, no database, no clock: a rule and a set of facts in, a verdict
 * out. Everything that could make the same inputs give different answers on
 * different days has been pushed out to the caller, which is what makes this
 * worth unit testing at all.
 *
 * <p>Three things fail rather than throw: an attribute the context could not
 * supply, a comparison value that does not parse as the attribute's type, and a
 * value the operator cannot be applied to. All three are misconfigurations, and
 * a misconfiguration must decline the applicant with a recorded reason - never
 * abort the run and take the other rules with it.
 */
public final class RuleEvaluator {

    private RuleEvaluator() {
    }

    public static RuleVerdict evaluate(Rule rule, RuleContext context) {
        String code = rule.getAttribute().getCode();
        RuleDataType type = rule.getAttribute().getDataType();
        String actual = context.render(code);

        if (!context.has(code)) {
            return RuleVerdict.fail(null,
                    "%s could not be determined".formatted(rule.getAttribute().getName()));
        }
        Object rawValue = context.value(code).orElse(null);
        if (rawValue == null) {
            return RuleVerdict.fail(null,
                    "%s has not been provided".formatted(rule.getAttribute().getName()));
        }
        if (!type.supports(rule.getOperator())) {
            return RuleVerdict.fail(actual,
                    "%s cannot be tested with %s".formatted(
                            rule.getAttribute().getName(), rule.getOperator().phrase()));
        }

        Comparable<?> subject = type.parse(String.valueOf(rawValue));
        if (subject == null) {
            return RuleVerdict.fail(actual,
                    "%s is not a valid %s".formatted(rule.getAttribute().getName(), type));
        }

        Boolean satisfied = test(rule, type, subject);
        if (satisfied == null) {
            // The rule itself is unreadable, not the customer's data.
            return RuleVerdict.fail(actual, "This criterion is misconfigured and could not be applied");
        }

        boolean held = rule.isNegate() ? !satisfied : satisfied;
        return held ? RuleVerdict.pass(actual) : RuleVerdict.fail(actual, rule.messageOnFailure());
    }

    /** Null means the rule's own values could not be read, not that it failed. */
    private static Boolean test(Rule rule, RuleDataType type, Comparable<?> subject) {
        RuleOperator operator = rule.getOperator();

        if (operator.isList()) {
            List<Comparable<?>> candidates = rule.comparisonTokens().stream()
                    .map(type::parse)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (candidates.isEmpty()) {
                return null;
            }
            boolean present = candidates.stream().anyMatch(candidate -> equal(subject, candidate));
            return operator == RuleOperator.IN ? present : !present;
        }

        Comparable<?> lower = type.parse(rule.getComparisonValue());
        if (lower == null) {
            return null;
        }

        if (operator.isRange()) {
            Comparable<?> upper = type.parse(rule.getComparisonValue2());
            if (upper == null) {
                return null;
            }
            Integer fromLower = compare(subject, lower);
            Integer toUpper = compare(subject, upper);
            if (fromLower == null || toUpper == null) {
                return null;
            }
            // Inclusive at both ends: "between 21 and 60" includes both birthdays.
            return fromLower >= 0 && toUpper <= 0;
        }

        return switch (operator) {
            case EQ -> equal(subject, lower);
            case NEQ -> !equal(subject, lower);
            case GT, GTE, LT, LTE -> {
                Integer ordering = compare(subject, lower);
                if (ordering == null) {
                    yield null;
                }
                yield switch (operator) {
                    case GT -> ordering > 0;
                    case GTE -> ordering >= 0;
                    case LT -> ordering < 0;
                    default -> ordering <= 0;
                };
            }
            default -> null;
        };
    }

    /**
     * Equality that does what a configuration author means.
     *
     * <p>Numbers compare by value, so 20000 equals 20000.00 - which
     * {@code BigDecimal.equals} does not. Text compares without regard to case,
     * because {@code VERIFIED} and {@code Verified} are the same status and a
     * capital letter is not grounds to decline someone.
     */
    private static boolean equal(Object subject, Object candidate) {
        if (subject instanceof BigDecimal left && candidate instanceof BigDecimal right) {
            return left.compareTo(right) == 0;
        }
        if (subject instanceof String left && candidate instanceof String right) {
            return left.equalsIgnoreCase(right);
        }
        return subject.equals(candidate);
    }

    /** Null when the two are not of one kind, which is a configuration fault. */
    @SuppressWarnings("unchecked")
    private static Integer compare(Comparable<?> subject, Comparable<?> candidate) {
        if (!subject.getClass().equals(candidate.getClass())) {
            return null;
        }
        return ((Comparable<Object>) subject).compareTo(candidate);
    }
}
