package com.naztech.lending.application;

import com.naztech.lending.application.dto.ActionRequest;
import com.naztech.lending.application.dto.ApplicationSummaryResponse;
import com.naztech.lending.application.dto.AvailableActionResponse;
import com.naztech.lending.application.dto.LoanApplicationDetailResponse;
import com.naztech.lending.application.dto.LoanPurposeResponse;
import com.naztech.lending.application.dto.NewApplicationRequest;
import com.naztech.lending.application.service.Actor;
import com.naztech.lending.application.service.ApplicationWorkflowService;
import com.naztech.lending.application.service.LoanApplicationService;
import com.naztech.lending.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loan applications, and the workflow that moves them.
 *
 * <p>Three gates rather than the usual two. The permission decides whether a
 * caller may touch applications at all; their organisational scope decides
 * which; and the role/state map decides what they may do to the one in front of
 * them. The third is the workflow engine's, read from configuration, and it is
 * why {@code available-actions} exists: a screen asks what to draw rather than
 * deciding for itself.
 *
 * <p>Creating is a narrower permission than viewing. A credit analyst who could
 * raise the file they are about to assess is a control failure, so
 * {@code application.create} is granted to the roles that source business and
 * not to the ones that judge it.
 */
@RestController
@RequestMapping("/api/v1/loan-applications")
@Tag(name = "Loan applications", description = "The loan file and the six-step workflow")
public class LoanApplicationController {

    private final LoanApplicationService applications;
    private final ApplicationWorkflowService workflow;

    public LoanApplicationController(LoanApplicationService applications,
                                     ApplicationWorkflowService workflow) {
        this.applications = applications;
        this.workflow = workflow;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('application.view')")
    @Operation(summary = "The queue",
            description = "Every application the caller is entitled to see, newest first. "
                    + "Narrow to one workflow state with ?state=, which is what a queue screen "
                    + "does. A branch-scoped reader gets their own branches and nobody else's.")
    public ApiResponse<List<ApplicationSummaryResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 40) String state) {
        return ApiResponse.success(
                applications.list(UUID.fromString(jwt.getSubject()), state));
    }

    @GetMapping("/purposes")
    @PreAuthorize("hasAuthority('application.view')")
    @Operation(summary = "The purposes a customer may choose",
            description = "Configuration, so a bank adds one with an insert. Some require the "
                    + "applicant to say more, which the flag reports rather than the screen "
                    + "guessing.")
    public ApiResponse<List<LoanPurposeResponse>> purposes() {
        return ApiResponse.success(
                applications.activePurposes().stream().map(LoanPurposeResponse::from).toList());
    }

    @GetMapping("/{applicationNo}")
    @PreAuthorize("hasAuthority('application.view')")
    @Operation(summary = "One application in full",
            description = "The terms it was judged under, the applicant as declared, the "
                    + "finances the ratio was computed from, every move it has made and "
                    + "everything said about it. An application outside the caller's scope "
                    + "answers 404, exactly as one that does not exist.")
    public ApiResponse<LoanApplicationDetailResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable String applicationNo) {
        return ApiResponse.success(
                applications.detail(UUID.fromString(jwt.getSubject()), applicationNo));
    }

    @GetMapping("/{applicationNo}/available-actions")
    @PreAuthorize("hasAuthority('application.view')")
    @Operation(summary = "What the caller may do to this application",
            description = "Resolved from the role/state map and the transition table, not from "
                    + "anything the screen knows. Each action carries the label to show, where "
                    + "the application would land, and whether a reason is required.")
    public ApiResponse<List<AvailableActionResponse>> availableActions(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String applicationNo) {
        return ApiResponse.success(workflow.availableActions(Actor.of(jwt), applicationNo));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('application.create')")
    @Operation(summary = "Raise an application",
            description = "Copies the applicant and their finances onto the file, quotes the "
                    + "loan from the live product version, and starts it in the workflow. The "
                    + "quotation is the backend's: a client cannot supply a rate.")
    public ApiResponse<LoanApplicationDetailResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewApplicationRequest request) {
        return ApiResponse.success(applications.create(Actor.of(jwt), request));
    }

    @PostMapping("/{applicationNo}/actions")
    @PreAuthorize("hasAuthority('application.act')")
    @Operation(summary = "Take an action",
            description = "Moves the application, if the role/state map allows the action from "
                    + "where it currently sits and the transition table offers the move. A "
                    + "refused action answers 403 when the role is wrong and 409 when the move "
                    + "is not one this state can make.")
    public ApiResponse<LoanApplicationDetailResponse> act(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable String applicationNo,
                                                          @Valid @RequestBody ActionRequest request) {
        return ApiResponse.success(workflow.act(Actor.of(jwt), applicationNo, request));
    }

    @PostMapping("/{applicationNo}/comments")
    @PreAuthorize("hasAuthority('application.act')")
    @Operation(summary = "Add a note",
            description = "Without moving the application. Internal by default: a bank needs "
                    + "somewhere to write a remark that is not a letter to the customer.")
    public ApiResponse<LoanApplicationDetailResponse> comment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String applicationNo,
            @Valid @RequestBody CommentRequest request) {
        return ApiResponse.success(workflow.comment(
                Actor.of(jwt), applicationNo, request.comment(),
                request.internalOnly() == null || request.internalOnly()));
    }

    /** A note, and whether the customer may see it. */
    public record CommentRequest(
            @NotBlank(message = "A comment is required")
            @Size(max = 2000, message = "A comment may be at most 2000 characters")
            String comment,
            Boolean internalOnly) {
    }
}
