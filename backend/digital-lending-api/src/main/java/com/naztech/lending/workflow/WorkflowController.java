package com.naztech.lending.workflow;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.workflow.dto.RoleStateGrantResponse;
import com.naztech.lending.workflow.dto.StateTransitionResponse;
import com.naztech.lending.workflow.dto.WorkflowStateResponse;
import com.naztech.lending.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workflow configuration, read.
 *
 * <p>Read only, and for the same reason the rule configuration is: changing who
 * may recommend an application to head office is a change to the bank's control
 * environment, and it needs the maker and checker of Milestone 21 before it gets
 * an API.
 *
 * <p>Reading it is open to every staff role, because the honest answer to "why
 * can I not recommend this" is the configuration itself.
 */
@RestController
@RequestMapping("/api/v1/workflow")
@Tag(name = "Workflow", description = "The configured states, transitions and role permissions")
public class WorkflowController {

    private final WorkflowService workflow;

    public WorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/states")
    @PreAuthorize("hasAuthority('workflow.view')")
    @Operation(summary = "The states of the workflow",
            description = "In workflow order, with the step each belongs to and the stage the "
                    + "customer is told about.")
    public ApiResponse<List<WorkflowStateResponse>> states() {
        return ApiResponse.success(
                workflow.allStates().stream().map(WorkflowStateResponse::from).toList());
    }

    @GetMapping("/transitions")
    @PreAuthorize("hasAuthority('workflow.view')")
    @Operation(summary = "The legal moves",
            description = "Which action takes an application from which state to which other. "
                    + "This is the specification's recommend/return map, carrying every action "
                    + "rather than two.")
    public ApiResponse<List<StateTransitionResponse>> transitions() {
        return ApiResponse.success(
                workflow.allTransitions().stream().map(StateTransitionResponse::from).toList());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('workflow.view')")
    @Operation(summary = "Who may do what, where",
            description = "The role/state map. Authoritative: the engine reads this and "
                    + "nothing else, which is what keeps role names out of the workflow code.")
    public ApiResponse<List<RoleStateGrantResponse>> permissions() {
        return ApiResponse.success(
                workflow.allGrants().stream().map(RoleStateGrantResponse::from).toList());
    }
}
