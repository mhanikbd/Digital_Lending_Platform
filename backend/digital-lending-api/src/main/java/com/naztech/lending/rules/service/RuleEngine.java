package com.naztech.lending.rules.service;

import com.naztech.lending.rules.domain.LogicalOperator;
import com.naztech.lending.rules.domain.Rule;
import com.naztech.lending.rules.domain.RuleGroup;
import com.naztech.lending.rules.domain.RulePurpose;
import com.naztech.lending.rules.dto.RuleGroupResult;
import com.naztech.lending.rules.dto.RuleLineResult;
import com.naztech.lending.rules.dto.RuleRunResult;
import com.naztech.lending.rules.repository.RuleGroupRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs configured rules against a set of facts.
 *
 * <p>Nothing in here knows what a loan is. It reads groups from the database,
 * asks {@link RuleEvaluator} about each rule, combines the answers the way the
 * group says to, and files the result. Adding a criterion is an INSERT; the
 * specification is emphatic on this point, and so is this class.
 *
 * <p>Every rule is evaluated even once the group has already failed. Short
 * circuiting would be faster and would leave a banker unable to answer "what
 * else was wrong" without running the check again after each fix.
 */
@Service
public class RuleEngine {

    private final RuleGroupRepository groups;
    private final RuleEvaluationRecorder recorder;
    private final Clock clock;

    public RuleEngine(RuleGroupRepository groups, RuleEvaluationRecorder recorder, Clock clock) {
        this.groups = groups;
        this.recorder = recorder;
        this.clock = clock;
    }

    /**
     * Evaluates every group that applies to a product version.
     *
     * <p>Both the groups tied to that version and the groups tied to none, which
     * apply bank-wide, in priority order.
     *
     * <p>When no group applies the run passes, and says so by returning no
     * groups. A product nobody has written criteria for is not a product that
     * refuses everybody - but the caller can tell the two apart, which matters
     * when the reason is that somebody forgot.
     */
    @Transactional(readOnly = true)
    public RuleRunResult run(UUID subjectId, UUID productVersionId, RulePurpose purpose,
                             RuleContext context) {
        List<RuleGroup> applicable = groups.findApplicableTo(productVersionId, purpose);

        List<RuleGroupResult> results = new ArrayList<>();
        List<EvaluatedRule> auditLines = new ArrayList<>();
        // A group's message repeats when several groups share one; the customer
        // should be told each distinct thing once.
        Set<String> reasons = new LinkedHashSet<>();
        boolean passed = true;

        for (RuleGroup group : applicable) {
            List<Rule> rules = group.activeRules();
            if (rules.isEmpty()) {
                // An empty group asserts nothing. Treating it as satisfied is
                // the only reading that does not decline everybody the moment an
                // administrator creates a group before filling it in.
                continue;
            }

            List<RuleLineResult> lines = new ArrayList<>();
            int satisfied = 0;
            for (Rule rule : rules) {
                RuleVerdict verdict = RuleEvaluator.evaluate(rule, context);
                lines.add(RuleLineResult.from(rule, verdict));
                auditLines.add(new EvaluatedRule(group.getCode(), rule, verdict));
                if (verdict.passed()) {
                    satisfied++;
                }
            }

            boolean groupPassed = group.getLogicalOperator() == LogicalOperator.AND
                    ? satisfied == rules.size()
                    : satisfied > 0;

            results.add(new RuleGroupResult(
                    group.getCode(),
                    group.getName(),
                    group.getLogicalOperator().name(),
                    groupPassed,
                    groupPassed ? null : group.messageOnFailure(),
                    lines));

            if (!groupPassed) {
                passed = false;
                reasons.addAll(reasonsFor(group, lines));
            }
        }

        UUID evaluationId = recorder.record(
                subjectId, productVersionId, passed, context, auditLines, clock.instant());

        return new RuleRunResult(passed, List.copyOf(reasons), List.copyOf(results), evaluationId);
    }

    /**
     * What the customer is told about one failed group.
     *
     * <p>For AND, each failed rule is a separate thing to fix and is named. For
     * OR, no single rule failing is the reason - the reason is that none of them
     * held - so only the group's own message is given. Assembling six "you are
     * not a student, you are not a pensioner, you are not..." lines out of an OR
     * would be both longer and wrong.
     */
    private List<String> reasonsFor(RuleGroup group, List<RuleLineResult> lines) {
        if (group.getLogicalOperator() == LogicalOperator.OR) {
            return List.of(group.messageOnFailure());
        }
        List<String> failures = lines.stream()
                .filter(line -> !line.passed())
                .map(RuleLineResult::message)
                .filter(java.util.Objects::nonNull)
                .toList();
        return failures.isEmpty() ? List.of(group.messageOnFailure()) : failures;
    }
}
