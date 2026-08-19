package com.naztech.lending.workflow.repository;

import com.naztech.lending.workflow.domain.StateTransition;
import com.naztech.lending.workflow.domain.WorkflowAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StateTransitionRepository extends JpaRepository<StateTransition, UUID> {

    /**
     * The moves out of a state, in the order the buttons should appear.
     *
     * <p>Both ends are fetched: a transition is meaningless without the state it
     * arrives at, and every caller needs it.
     */
    @Query("""
            SELECT t FROM StateTransition t
            JOIN FETCH t.fromState
            JOIN FETCH t.toState
            WHERE t.fromState.code = :fromState AND t.status = 'ACTIVE'
            ORDER BY t.displayOrder ASC
            """)
    List<StateTransition> findFrom(@Param("fromState") String fromState);

    /**
     * The move a state and an action name.
     *
     * <p>Returns a list rather than one row, because a state may offer the same
     * action into several destinations - SO_RECOMMENDED can be recommended by
     * three different branch roles, each landing somewhere different. Which one
     * applies is decided by who is asking, and that is the service's business.
     */
    @Query("""
            SELECT t FROM StateTransition t
            JOIN FETCH t.fromState
            JOIN FETCH t.toState
            WHERE t.fromState.code = :fromState AND t.action = :action AND t.status = 'ACTIVE'
            ORDER BY t.displayOrder ASC
            """)
    List<StateTransition> findFromByAction(@Param("fromState") String fromState,
                                           @Param("action") WorkflowAction action);

    @Query("""
            SELECT t FROM StateTransition t
            JOIN FETCH t.fromState
            JOIN FETCH t.toState
            ORDER BY t.fromState.displayOrder ASC, t.displayOrder ASC
            """)
    List<StateTransition> findAllForDisplay();

    default Optional<StateTransition> findSingle(String fromState, WorkflowAction action) {
        List<StateTransition> candidates = findFromByAction(fromState, action);
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }
}
