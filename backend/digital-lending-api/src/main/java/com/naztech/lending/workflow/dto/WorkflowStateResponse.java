package com.naztech.lending.workflow.dto;

import com.naztech.lending.workflow.domain.WorkflowState;
import io.swagger.v3.oas.annotations.media.Schema;

/** One configured state of the workflow. */
@Schema(description = "A workflow state")
public record WorkflowStateResponse(
        @Schema(example = "SO_CREATED") String code,
        @Schema(example = "With the sourcing officer") String name,
        String description,
        @Schema(example = "1") int stepNo,
        @Schema(example = "Origination") String stepName,
        @Schema(example = "INITIAL") String stateType,
        @Schema(description = "What the customer is told", example = "IN_PROGRESS")
        String customerStage,
        @Schema(description = "Hours before the state is overdue; null when none applies")
        Short slaHours,
        @Schema(example = "ACTIVE") String status) {

    public static WorkflowStateResponse from(WorkflowState state) {
        return new WorkflowStateResponse(state.getCode(), state.getName(), state.getDescription(),
                state.getStepNo(), state.getStepName(), state.getStateType().name(),
                state.getCustomerStage().name(), state.getSlaHours(), state.getStatus());
    }
}
