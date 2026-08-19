package com.naztech.lending.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naztech.lending.common.correlation.CorrelationId;
import com.naztech.lending.rules.domain.EvaluationOutcome;
import com.naztech.lending.rules.domain.RuleEvaluation;
import com.naztech.lending.rules.domain.RuleEvaluationDetail;
import com.naztech.lending.rules.repository.RuleEvaluationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit record of a rule run.
 *
 * <p>Its own bean, and its own transaction, for a reason learnt in the
 * authentication module: an audit row written inside the caller's transaction
 * disappears when the caller rolls back, and a decline that leaves no trace is
 * the one case where the record matters most. REQUIRES_NEW is applied by the
 * proxy, so it only takes effect when the call arrives from outside the bean -
 * hence a separate class rather than a method on the engine.
 */
@Component
public class RuleEvaluationRecorder {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluationRecorder.class);

    private final RuleEvaluationRepository evaluations;
    private final ObjectMapper json;

    public RuleEvaluationRecorder(RuleEvaluationRepository evaluations, ObjectMapper json) {
        this.evaluations = evaluations;
        this.json = json;
    }

    /**
     * Records one run and returns its id.
     *
     * <p>Returns null rather than propagating when the write itself fails. A
     * customer must not be refused a decision because the platform could not
     * file the paperwork; the failure is logged loudly instead.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(UUID subjectId, UUID productVersionId, boolean passed,
                       RuleContext context, List<EvaluatedRule> lines, Instant at) {
        try {
            RuleEvaluation evaluation = new RuleEvaluation(
                    subjectId,
                    productVersionId,
                    passed ? EvaluationOutcome.PASS : EvaluationOutcome.FAIL,
                    snapshotOf(context),
                    CorrelationId.current(),
                    at);

            for (EvaluatedRule line : lines) {
                evaluation.add(new RuleEvaluationDetail(
                        line.groupCode(),
                        line.rule().getAttribute().getCode(),
                        line.rule().getOperator(),
                        line.rule().expectedValue(),
                        line.verdict().actualValue(),
                        line.verdict().passed(),
                        line.verdict().message()));
            }

            return evaluations.save(evaluation).getId();
        } catch (RuntimeException failedToRecord) {
            log.error("Could not record rule evaluation for subject {}", subjectId, failedToRecord);
            return null;
        }
    }

    private String snapshotOf(RuleContext context) {
        try {
            return json.writeValueAsString(context.snapshot());
        } catch (JsonProcessingException cannotSerialise) {
            // The column is NOT NULL and an empty object is honest: we know the
            // run happened, and that we could not capture what it looked at.
            log.warn("Could not serialise rule context snapshot", cannotSerialise);
            return "{}";
        }
    }
}
