package com.naztech.lending.rules.service;

/**
 * What one rule said, and what it was looking at when it said it.
 *
 * @param passed       whether the rule was satisfied, after any negation
 * @param actualValue  the fact as it stood, or null when none could be supplied
 * @param message      why it failed, or null when it did not
 */
public record RuleVerdict(boolean passed, String actualValue, String message) {

    static RuleVerdict pass(String actualValue) {
        return new RuleVerdict(true, actualValue, null);
    }

    static RuleVerdict fail(String actualValue, String message) {
        return new RuleVerdict(false, actualValue, message);
    }
}
