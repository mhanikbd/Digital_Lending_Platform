package com.naztech.lending.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.rules.domain.LogicalOperator;
import com.naztech.lending.rules.domain.Rule;
import com.naztech.lending.rules.domain.RuleAttribute;
import com.naztech.lending.rules.domain.RuleDataType;
import com.naztech.lending.rules.domain.RuleGroup;
import com.naztech.lending.rules.domain.RuleOperator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The rule engine's comparison logic.
 *
 * <p>This is where a misconfiguration meets a customer. The tests below are as
 * interested in the awkward cases as the obvious ones: an attribute nobody could
 * supply, a comparison value that does not parse, an operator that makes no
 * sense for the type. Every one of them has to decline with a recorded reason
 * rather than throw, because an exception here would abandon the whole
 * evaluation and take the other five criteria with it.
 */
class RuleEvaluatorTest {

    private static final RuleAttribute AGE = RuleAttribute.of(
            "customer.age", "Age in years", null, RuleDataType.NUMBER, "CUSTOMER");
    private static final RuleAttribute INCOME = RuleAttribute.of(
            "customer.monthly_income", "Total monthly income", null, RuleDataType.NUMBER, "CUSTOMER");
    private static final RuleAttribute KYC = RuleAttribute.of(
            "customer.kyc_status", "KYC status", null, RuleDataType.STRING, "CUSTOMER");
    private static final RuleAttribute NID = RuleAttribute.of(
            "customer.has_verified_nid", "NID verified", null, RuleDataType.BOOLEAN, "CUSTOMER");
    private static final RuleAttribute ONBOARDED = RuleAttribute.of(
            "customer.onboarded_on", "Onboarded on", null, RuleDataType.DATE, "CUSTOMER");

    private static final RuleGroup GROUP =
            RuleGroup.of("TEST", "Test group", LogicalOperator.AND, null);

    @Nested
    class Numbers {

        @ParameterizedTest
        @CsvSource({
                "GTE, 20000, 20000, true",
                "GTE, 20000, 19999, false",
                "GT,  20000, 20000, false",
                "LTE, 60,    60,    true",
                "LT,  60,    60,    false",
                "EQ,  35,    35,    true",
                "NEQ, 35,    35,    false",
        })
        void comparesNumbersInBothDirections(String operator, String expected, String actual,
                                             boolean passes) {
            Rule rule = Rule.of(GROUP, INCOME, RuleOperator.valueOf(operator), expected, null);
            RuleContext context = RuleContext.builder()
                    .number(INCOME.getCode(), new BigDecimal(actual)).build();

            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isEqualTo(passes);
        }

        @Test
        void treatsEqualityByValueRatherThanByScale() {
            // BigDecimal.equals says 20000 and 20000.00 are different objects.
            // A configuration author does not mean that, and a customer must not
            // be declined over a trailing zero.
            Rule rule = Rule.of(GROUP, INCOME, RuleOperator.EQ, "20000", null);
            RuleContext context = RuleContext.builder()
                    .number(INCOME.getCode(), new BigDecimal("20000.0000")).build();

            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"21, false", "20, false", "35, true", "60, true", "61, false"})
        void betweenIsInclusiveAtBothEnds(String age, boolean ignored) {
            Rule rule = Rule.of(GROUP, AGE, RuleOperator.BETWEEN, "21", "60");
            RuleContext context = RuleContext.builder()
                    .number(AGE.getCode(), Integer.valueOf(age)).build();

            boolean inRange = Integer.parseInt(age) >= 21 && Integer.parseInt(age) <= 60;
            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isEqualTo(inRange);
        }
    }

    @Nested
    class Text {

        @Test
        void matchesAStatusRegardlessOfCase() {
            // VERIFIED and Verified are the same status. A capital letter is not
            // grounds to decline somebody.
            Rule rule = Rule.of(GROUP, KYC, RuleOperator.EQ, "verified", null);
            RuleContext context = RuleContext.builder().text(KYC.getCode(), "VERIFIED").build();

            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isTrue();
        }

        @Test
        void readsAnInListAndIgnoresSpacingAroundTheCommas() {
            Rule rule = Rule.of(GROUP, KYC, RuleOperator.IN, "VERIFIED, IN_PROGRESS", null);
            RuleContext context = RuleContext.builder()
                    .text(KYC.getCode(), "IN_PROGRESS").build();

            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isTrue();
        }

        @Test
        void notInIsTheExactInverseOfIn() {
            Rule rule = Rule.of(GROUP, KYC, RuleOperator.NOT_IN, "REJECTED,PENDING", null);

            assertThat(RuleEvaluator.evaluate(rule,
                    RuleContext.builder().text(KYC.getCode(), "VERIFIED").build()).passed()).isTrue();
            assertThat(RuleEvaluator.evaluate(rule,
                    RuleContext.builder().text(KYC.getCode(), "PENDING").build()).passed()).isFalse();
        }

        @Test
        void refusesToOrderTwoNames() {
            // "Is this status greater than that one" is not a question, and
            // allowing it would let an administrator build a rule that looks
            // like it works.
            Rule rule = Rule.of(GROUP, KYC, RuleOperator.GT, "PENDING", null);
            RuleContext context = RuleContext.builder().text(KYC.getCode(), "VERIFIED").build();

            RuleVerdict verdict = RuleEvaluator.evaluate(rule, context);
            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.message()).contains("cannot be tested with");
        }
    }

    @Nested
    class BooleansAndDates {

        @Test
        void readsYesAndTrueAsTheSameThing() {
            Rule rule = Rule.of(GROUP, NID, RuleOperator.EQ, "yes", null);
            RuleContext context = RuleContext.builder().flag(NID.getCode(), true).build();

            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isTrue();
        }

        @Test
        void comparesDatesChronologically() {
            Rule rule = Rule.of(GROUP, ONBOARDED, RuleOperator.LTE, "2026-01-01", null);
            RuleContext context = RuleContext.builder()
                    .date(ONBOARDED.getCode(), LocalDate.of(2025, 6, 30)).build();

            assertThat(RuleEvaluator.evaluate(rule, context).passed()).isTrue();
        }
    }

    @Nested
    class Negation {

        @Test
        void invertsTheRuleItIsAppliedTo() {
            Rule plain = Rule.of(GROUP, KYC, RuleOperator.EQ, "REJECTED", null);
            Rule inverted = Rule.of(GROUP, KYC, RuleOperator.EQ, "REJECTED", null).negated();
            RuleContext context = RuleContext.builder().text(KYC.getCode(), "VERIFIED").build();

            assertThat(RuleEvaluator.evaluate(plain, context).passed()).isFalse();
            assertThat(RuleEvaluator.evaluate(inverted, context).passed()).isTrue();
        }

        @Test
        void doesNotTurnAMissingValueIntoAPass() {
            // The dangerous case. If negation were applied blindly, "NOT status
            // = REJECTED" would pass for a customer whose status is unknown -
            // and unknown is exactly when the bank should not be lending.
            Rule inverted = Rule.of(GROUP, KYC, RuleOperator.EQ, "REJECTED", null).negated();

            assertThat(RuleEvaluator.evaluate(inverted, RuleContext.builder().build()).passed())
                    .isFalse();
        }
    }

    @Nested
    class ThingsThatMustFailRatherThanThrow {

        @Test
        void reportsAnAttributeTheContextCouldNotSupply() {
            Rule rule = Rule.of(GROUP, AGE, RuleOperator.GTE, "21", null);

            RuleVerdict verdict = RuleEvaluator.evaluate(rule, RuleContext.builder().build());

            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.actualValue()).isNull();
            assertThat(verdict.message()).contains("could not be determined");
        }

        @Test
        void distinguishesNotSuppliedFromNotDeclared() {
            // Absent means no module could answer. Null means we asked the
            // customer and they have not told us. Both decline; they read
            // differently in the audit record, and somebody will need that.
            Rule rule = Rule.of(GROUP, AGE, RuleOperator.GTE, "21", null);
            RuleContext declaredNothing = RuleContext.builder().put(AGE.getCode(), null).build();

            assertThat(RuleEvaluator.evaluate(rule, declaredNothing).message())
                    .contains("has not been provided");
        }

        @Test
        void reportsAComparisonValueThatIsNotANumber() {
            Rule misconfigured = Rule.of(GROUP, AGE, RuleOperator.GTE, "twenty one", null);
            RuleContext context = RuleContext.builder().number(AGE.getCode(), 35).build();

            RuleVerdict verdict = RuleEvaluator.evaluate(misconfigured, context);

            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.message()).contains("misconfigured");
            // The value it was looking at is still recorded, because the fault
            // is in the rule and the customer's data is fine.
            assertThat(verdict.actualValue()).isEqualTo("35");
        }

        @Test
        void reportsAContextValueThatIsNotANumber() {
            Rule rule = Rule.of(GROUP, AGE, RuleOperator.GTE, "21", null);
            RuleContext context = RuleContext.builder().put(AGE.getCode(), "unknown").build();

            RuleVerdict verdict = RuleEvaluator.evaluate(rule, context);

            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.message()).contains("not a valid NUMBER");
        }

        @Test
        void reportsAnEmptyInList() {
            Rule misconfigured = Rule.of(GROUP, KYC, RuleOperator.IN, " , ", null);
            RuleContext context = RuleContext.builder().text(KYC.getCode(), "VERIFIED").build();

            assertThat(RuleEvaluator.evaluate(misconfigured, context).message())
                    .contains("misconfigured");
        }
    }

    @Nested
    class WhatTheRuleSays {

        @Test
        void describesItselfInWordsForTheAuditRecord() {
            assertThat(Rule.of(GROUP, AGE, RuleOperator.BETWEEN, "21", "60").describe())
                    .isEqualTo("Age in years is between 21 and 60");
            assertThat(Rule.of(GROUP, KYC, RuleOperator.IN, "VERIFIED, IN_PROGRESS", null).describe())
                    .isEqualTo("KYC status is one of VERIFIED, IN_PROGRESS");
            assertThat(Rule.of(GROUP, KYC, RuleOperator.EQ, "VERIFIED", null).negated().describe())
                    .isEqualTo("NOT (KYC status is VERIFIED)");
        }

        @Test
        void prefersTheConfiguredMessageOverItsOwnDescription() {
            Rule rule = Rule.of(GROUP, AGE, RuleOperator.BETWEEN, "21", "60")
                    .sayingOnFailure("Applicants must be between 21 and 60 years old.");

            assertThat(rule.messageOnFailure())
                    .isEqualTo("Applicants must be between 21 and 60 years old.");
        }

        @Test
        void fallsBackToItsDescriptionWhenNoMessageIsConfigured() {
            assertThat(Rule.of(GROUP, AGE, RuleOperator.GTE, "21", null).messageOnFailure())
                    .isEqualTo("Age in years is at least 21");
        }

        @Test
        void recordsBothBoundsOfABetweenAsOneExpectedValue() {
            assertThat(Rule.of(GROUP, AGE, RuleOperator.BETWEEN, "21", "60").expectedValue())
                    .isEqualTo("21 - 60");
        }
    }
}
