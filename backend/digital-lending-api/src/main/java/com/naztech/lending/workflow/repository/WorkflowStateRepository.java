package com.naztech.lending.workflow.repository;

import com.naztech.lending.workflow.domain.WorkflowState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStateRepository extends JpaRepository<WorkflowState, String> {

    /** Every state in workflow order, for a configuration screen and the queues. */
    List<WorkflowState> findAllByOrderByDisplayOrderAsc();
}
