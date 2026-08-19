package com.naztech.lending.application.repository;

import com.naztech.lending.application.domain.ApplicationQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationQueryRepository extends JpaRepository<ApplicationQuery, UUID> {

    /** The queries on a file, with their answers, in the order they were asked. */
    @EntityGraph(attributePaths = {"responses"})
    List<ApplicationQuery> findByApplicationIdOrderByQueryNoAsc(UUID applicationId);

    /** The oldest unanswered query, which is the one a branch has to deal with. */
    Optional<ApplicationQuery> findFirstByApplicationIdAndStatusOrderByQueryNoAsc(
            UUID applicationId, com.naztech.lending.application.domain.QueryStatus status);

    long countByApplicationId(UUID applicationId);
}
