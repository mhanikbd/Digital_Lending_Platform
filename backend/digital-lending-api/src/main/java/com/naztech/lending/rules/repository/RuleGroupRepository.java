package com.naztech.lending.rules.repository;

import com.naztech.lending.rules.domain.RuleGroup;
import com.naztech.lending.rules.domain.RulePurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleGroupRepository extends JpaRepository<RuleGroup, UUID> {

    Optional<RuleGroup> findByCode(String code);

    /**
     * The groups that apply to one product version, in the order they run.
     *
     * <p>Two kinds are returned together: groups tied to this version, and
     * groups tied to no version at all, which apply bank-wide. The rules and
     * their attributes come with them, because evaluating a group means reading
     * every rule in it and a lazy walk would be a query per rule.
     */
    @Query("""
            SELECT DISTINCT g FROM RuleGroup g
            LEFT JOIN FETCH g.rules r
            LEFT JOIN FETCH r.attribute
            WHERE g.status = com.naztech.lending.rules.domain.RuleStatus.ACTIVE
              AND g.purpose = :purpose
              AND (g.productVersion.id = :versionId OR g.productVersion IS NULL)
            ORDER BY g.priority ASC
            """)
    List<RuleGroup> findApplicableTo(@Param("versionId") UUID versionId,
                                     @Param("purpose") RulePurpose purpose);

    /** Every group, for an administrator reviewing the configuration. */
    @Query("""
            SELECT DISTINCT g FROM RuleGroup g
            LEFT JOIN FETCH g.rules r
            LEFT JOIN FETCH r.attribute
            ORDER BY g.priority ASC
            """)
    List<RuleGroup> findAllWithRules();
}
