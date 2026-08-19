package com.naztech.lending.rules.repository;

import com.naztech.lending.rules.domain.RuleEvaluation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleEvaluationRepository extends JpaRepository<RuleEvaluation, UUID> {

    /** The most recent decisions about one subject, newest first. */
    List<RuleEvaluation> findTop20BySubjectIdOrderByEvaluatedAtDesc(UUID subjectId);
}
