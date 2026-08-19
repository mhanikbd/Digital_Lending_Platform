package com.naztech.lending.workflow.dto;

import com.naztech.lending.workflow.domain.StateTransition;
import io.swagger.v3.oas.annotations.media.Schema;

/** One legal move, as configured. */
@Schema(description = "A legal workflow transition")
public record StateTransitionResponse(
        @Schema(example = "SO_RECOMMENDED") String fromState,
        @Schema(example = "BM_RECOMMENDED") String toState,
        @Schema(example = "RECOMMEND") String action,
        @Schema(description = "The role this move belongs to, when several roles take the "
                + "same action from the same state into different destinations",
                example = "BM") String actorRole,
        @Schema(example = "Recommend to head office") String label,
        boolean reasonRequired,
        @Schema(example = "ACTIVE") String status) {

    public static StateTransitionResponse from(StateTransition transition) {
        return new StateTransitionResponse(
                transition.getFromState().getCode(), transition.getToState().getCode(),
                transition.getAction().name(), transition.getActorRoleCode(),
                transition.getLabel(), transition.isReasonRequired(), transition.getStatus());
    }
}
