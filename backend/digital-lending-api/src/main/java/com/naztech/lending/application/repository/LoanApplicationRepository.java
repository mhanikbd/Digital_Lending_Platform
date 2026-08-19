package com.naztech.lending.application.repository;

import com.naztech.lending.application.domain.LoanApplication;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    /**
     * The queue, for a reader whose scope is not narrowed.
     *
     * <p>The customer, product and state come with the row because a queue shows
     * all three on every line, and a lazy walk would issue three queries per
     * application to say so.
     */
    @EntityGraph(attributePaths = {"customer", "product", "state", "branch"})
    List<LoanApplication> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"customer", "product", "state", "branch"})
    List<LoanApplication> findByBranchIdInOrderByCreatedAtDesc(Collection<UUID> branchIds);

    /** The same two, narrowed to one workflow state - which is what a queue is. */
    @EntityGraph(attributePaths = {"customer", "product", "state", "branch"})
    List<LoanApplication> findByStateCodeOrderByCreatedAtDesc(String stateCode);

    @EntityGraph(attributePaths = {"customer", "product", "state", "branch"})
    List<LoanApplication> findByStateCodeAndBranchIdInOrderByCreatedAtDesc(
            String stateCode, Collection<UUID> branchIds);

    @EntityGraph(attributePaths = {"customer", "product", "productVersion", "state", "branch"})
    Optional<LoanApplication> findWithDetailByApplicationNo(String applicationNo);

    @EntityGraph(attributePaths = {"customer", "product", "productVersion", "state", "branch"})
    Optional<LoanApplication> findWithDetailById(UUID id);

    boolean existsByApplicationNo(String applicationNo);

    /**
     * How many applications exist for a year, so the next number can follow on.
     *
     * <p>Counting rather than reading a sequence keeps the numbering readable -
     * APP-2026-000042 rather than APP-2026-8814 - at the cost of needing the
     * uniqueness constraint to catch a race, which it does.
     */
    @Query("SELECT count(a) FROM LoanApplication a WHERE a.applicationNo LIKE :prefix%")
    long countWithPrefix(@Param("prefix") String prefix);

    /** Everything this customer has ever applied for. */
    @EntityGraph(attributePaths = {"product", "state"})
    List<LoanApplication> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
