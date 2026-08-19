package com.naztech.lending.rules.service;

import com.naztech.lending.rules.domain.Rule;

/**
 * One rule, the group it came from, and what it said.
 *
 * <p>Carried from the engine to the recorder so the audit row is built while the
 * rule is still in hand. The alternative - handing the recorder the response
 * objects and reconstructing the rule from them - would let the record and the
 * answer drift apart, which defeats the point of keeping the record.
 */
public record EvaluatedRule(String groupCode, Rule rule, RuleVerdict verdict) {
}
