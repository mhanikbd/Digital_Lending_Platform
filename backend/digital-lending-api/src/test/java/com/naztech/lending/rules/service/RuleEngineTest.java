package com.naztech.lending.rules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naztech.lending.rules.domain.LogicalOperator;
import com.naztech.lending.rules.domain.Rule;
import com.naztech.lending.rules.domain.RuleAttribute;
import com.naztech.lending.rules.domain.RuleDataType;
import com.naztech.lending.rules.domain.RuleGroup;
import com.naztech.lending.rules.domain.RuleOperator;
import com.naztech.lending.rules.domain.RulePurpose;
import com.naztech.lending.rules.dto.RuleGroupResult;
import com.naztech.lending.rules.dto.RuleRunResult;
import com.naztech.lending.rules.repository.RuleGroupRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How the engine combines rules into a decision.
 *
 * <p>{@link RuleEvaluatorTest} covers whether one rule holds. This covers what
 * the engine does with several: AND against OR, which reasons a customer is
 * given, what happens to a group somebody created but never filled in, and -
 * the one with real consequences - that a run is recorded whether it passed or
 * failed.
 */
@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    private static final UUID SUBJECT = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();

    private static final RuleAttribute AGE = RuleAttribute.of(
            "customer.age", "Age in years", null, RuleDataType.NUMBER, "CUSTOMER");
    private static final RuleAttribute INCOME = RuleAttribute.of(
            "customer.monthly_income", "Total monthly income", null,
            RuleDataType.NUMBER, "CUSTOMER");
    private static final RuleAttribute KYC = RuleAttribute.of(
            "customer.kyc_status", "KYC status", null, RuleDataType.STRING, "CUSTOMER");

    @Mock
    private RuleGroupRepository groups;

    @Mock
    private RuleEvaluationRecorder recorder;

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngine(groups, recorder,
                Clock.fixed(Instant.parse("2026-08-19T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void passesWhenEveryRuleOfAnAndGroupHolds() {
        given(baseGroup());

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, qualified());

        assertThat(result.passed()).isTrue();
        assertThat(result.reasons()).isEmpty();
        assertThat(result.groups()).singleElement()
                .satisfies(group -> assertThat(group.passed()).isTrue());
    }

    @Test
    void failsAnAndGroupTheMomentOneRuleDoesNot() {
        given(baseGroup());

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY,
                RuleContext.builder()
                        .number(AGE.getCode(), 19)
                        .number(INCOME.getCode(), new BigDecimal("50000"))
                        .text(KYC.getCode(), "VERIFIED")
                        .build());

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).containsExactly("You must be at least 21.");
    }

    @Test
    void evaluatesEveryRuleEvenAfterTheGroupHasAlreadyFailed() {
        // Short circuiting would be faster and would leave a banker unable to
        // answer "what else is wrong" without a fresh check after each fix.
        given(baseGroup());

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY,
                RuleContext.builder()
                        .number(AGE.getCode(), 19)
                        .number(INCOME.getCode(), new BigDecimal("100"))
                        .text(KYC.getCode(), "PENDING")
                        .build());

        RuleGroupResult group = result.groups().get(0);
        assertThat(group.criteria()).hasSize(3);
        assertThat(group.criteria()).noneMatch(line -> line.passed());
        assertThat(result.reasons()).hasSize(3);
    }

    @Test
    void oneRuleIsEnoughForAnOrGroup() {
        RuleGroup either = RuleGroup.of("EITHER", "Either", LogicalOperator.OR, null)
                .sayingOnFailure("You meet neither route to this product.")
                .with(Rule.of(null, AGE, RuleOperator.GTE, "65", null))
                .with(Rule.of(null, INCOME, RuleOperator.GTE, "20000", null));
        given(either);

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY,
                RuleContext.builder()
                        .number(AGE.getCode(), 30)
                        .number(INCOME.getCode(), new BigDecimal("25000"))
                        .build());

        assertThat(result.passed()).isTrue();
    }

    @Test
    void givesOnlyTheGroupMessageWhenAnOrGroupFails() {
        // No single rule failing is the reason - the reason is that none of them
        // held. Listing each would be both longer and wrong.
        RuleGroup either = RuleGroup.of("EITHER", "Either", LogicalOperator.OR, null)
                .sayingOnFailure("You meet neither route to this product.")
                .with(Rule.of(null, AGE, RuleOperator.GTE, "65", null)
                        .sayingOnFailure("You are not old enough."))
                .with(Rule.of(null, INCOME, RuleOperator.GTE, "20000", null)
                        .sayingOnFailure("You do not earn enough."));
        given(either);

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY,
                RuleContext.builder()
                        .number(AGE.getCode(), 30)
                        .number(INCOME.getCode(), new BigDecimal("5000"))
                        .build());

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).containsExactly("You meet neither route to this product.");
    }

    @Test
    void ignoresAGroupThatHasNoRulesInItYet() {
        // An empty group asserts nothing. Failing it would decline everybody the
        // moment an administrator created a group before filling it in.
        given(RuleGroup.of("EMPTY", "Not filled in", LogicalOperator.AND, null));

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, qualified());

        assertThat(result.passed()).isTrue();
        assertThat(result.groups()).isEmpty();
        assertThat(result.evaluatedNothing()).isTrue();
    }

    @Test
    void skipsARuleThatHasBeenDeactivatedWithoutDeletingItsHistory() {
        RuleGroup group = RuleGroup.of("BASE", "Base", LogicalOperator.AND, null)
                .with(Rule.of(null, AGE, RuleOperator.GTE, "21", null))
                .with(Rule.of(null, INCOME, RuleOperator.GTE, "999999", null)
                        .sayingOnFailure("Nobody earns this.")
                        .deactivated());
        given(group);

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, qualified());

        assertThat(result.passed()).isTrue();
        assertThat(result.groups().get(0).criteria()).hasSize(1);
    }

    @Test
    void saysTheSameThingOnceWhenTwoGroupsShareAMessage() {
        RuleGroup first = RuleGroup.of("A", "A", LogicalOperator.AND, null)
                .sayingOnFailure("Please complete your profile.")
                .with(Rule.of(null, AGE, RuleOperator.GTE, "21", null)
                        .sayingOnFailure("Please complete your profile."));
        RuleGroup second = RuleGroup.of("B", "B", LogicalOperator.AND, null)
                .sayingOnFailure("Please complete your profile.")
                .with(Rule.of(null, INCOME, RuleOperator.GTE, "20000", null)
                        .sayingOnFailure("Please complete your profile."));
        when(groups.findApplicableTo(VERSION, RulePurpose.ELIGIBILITY))
                .thenReturn(List.of(first, second));

        // Both rules are supplied with values and both fail, so each returns
        // its configured message rather than the evaluator's "could not be
        // determined" - which is a different message for a different situation.
        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY,
                RuleContext.builder()
                        .number(AGE.getCode(), 18)
                        .number(INCOME.getCode(), new BigDecimal("100"))
                        .build());

        assertThat(result.reasons()).containsExactly("Please complete your profile.");
        assertThat(result.groups()).hasSize(2);
    }

    @Test
    void saysWhatIsMissingRatherThanTheConfiguredMessageWhenAValueIsUnavailable() {
        // A rule that could not be applied and a rule that was applied and
        // failed are different situations. "You must be at least 21" would be
        // misleading told to somebody whose date of birth the bank never took.
        given(baseGroup());

        RuleRunResult result = engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY,
                RuleContext.builder().text(KYC.getCode(), "VERIFIED").build());

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).containsExactly(
                "Age in years could not be determined",
                "Total monthly income could not be determined");
    }

    @Test
    void recordsTheRunWhetherItPassedOrFailed() {
        given(baseGroup());

        engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, qualified());
        // A context that can answer nothing: every rule fails for want of a value.
        engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, RuleContext.builder().build());

        ArgumentCaptor<Boolean> outcomes = ArgumentCaptor.forClass(Boolean.class);
        verify(recorder, org.mockito.Mockito.times(2)).record(
                eq(SUBJECT), eq(VERSION), outcomes.capture(), any(), any(), any());

        assertThat(outcomes.getAllValues()).containsExactly(true, false);
    }

    @Test
    void handsTheRecorderOneLinePerRuleEvaluated() {
        // The audit detail is built from the rules themselves rather than from
        // the response, so the record and the answer cannot drift apart.
        given(baseGroup());

        engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, qualified());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvaluatedRule>> lines =
                ArgumentCaptor.forClass((Class<List<EvaluatedRule>>) (Class<?>) List.class);
        verify(recorder).record(any(), any(), anyBoolean(), any(), lines.capture(), any());

        assertThat(lines.getValue()).hasSize(3);
        assertThat(lines.getValue()).allSatisfy(
                line -> assertThat(line.groupCode()).isEqualTo("BASE"));
    }

    @Test
    void carriesTheAuditRecordIdBackToTheCaller() {
        given(baseGroup());
        UUID recorded = UUID.randomUUID();
        when(recorder.record(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(recorded);

        assertThat(engine.run(SUBJECT, VERSION, RulePurpose.ELIGIBILITY, qualified())
                .evaluationId()).isEqualTo(recorded);
    }

    private void given(RuleGroup group) {
        when(groups.findApplicableTo(VERSION, RulePurpose.ELIGIBILITY)).thenReturn(List.of(group));
    }

    private static RuleGroup baseGroup() {
        return RuleGroup.of("BASE", "Base eligibility", LogicalOperator.AND, null)
                .sayingOnFailure("You do not meet the basic criteria.")
                .with(Rule.of(null, AGE, RuleOperator.GTE, "21", null)
                        .sayingOnFailure("You must be at least 21.").atPriority(10))
                .with(Rule.of(null, INCOME, RuleOperator.GTE, "20000", null)
                        .sayingOnFailure("You must earn at least 20,000.").atPriority(20))
                .with(Rule.of(null, KYC, RuleOperator.EQ, "VERIFIED", null)
                        .sayingOnFailure("Your identity check is not complete.").atPriority(30));
    }

    private static RuleContext qualified() {
        return RuleContext.builder()
                .number(AGE.getCode(), 34)
                .number(INCOME.getCode(), new BigDecimal("45000"))
                .text(KYC.getCode(), "VERIFIED")
                .build();
    }
}
