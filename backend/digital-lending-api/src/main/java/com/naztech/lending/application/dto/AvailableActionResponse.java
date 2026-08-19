package com.naztech.lending.application.dto;

import com.naztech.lending.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One thing the caller may do to this application.
 *
 * <p>The label comes from the workflow configuration rather than from the
 * screen, so the word on the button, the word in the history and the word in the
 * notification are the same word.
 */
@Schema(description = "An action the caller may take on this application")
public record AvailableActionResponse(
        @Schema(example = "RECOMMEND") String action,
        @Schema(example = "Recommend to head office") String label,
        @Schema(description = "Where the application lands; null for VIEW and EDIT",
                example = "BM_RECOMMENDED") String toState,
        @Schema(description = "Whether the action is refused without a reason")
        boolean reasonRequired) {

    public static AvailableActionResponse from(WorkflowService.AvailableAction action) {
        return new AvailableActionResponse(
                action.action().name(), action.label(), action.toState(), action.reasonRequired());
    }
}
