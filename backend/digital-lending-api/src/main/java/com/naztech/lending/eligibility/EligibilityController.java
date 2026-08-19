package com.naztech.lending.eligibility;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.eligibility.dto.EligibilityRequest;
import com.naztech.lending.eligibility.dto.EligibilityResponse;
import com.naztech.lending.eligibility.service.EligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The eligibility engine of §16, and the amount engine of §17 behind it.
 *
 * <p>Two gates as everywhere else: the permission decides who may run a check,
 * and the caller's organisational scope decides whose customers they may run it
 * on. A customer outside that scope answers 404 exactly as one that does not
 * exist.
 *
 * <p>Every call is recorded, pass or fail, with the values it was decided on.
 * The response carries the id of that record.
 */
@RestController
@RequestMapping("/api/v1/eligibility")
@Tag(name = "Eligibility", description = "Whether a customer qualifies, and for how much")
public class EligibilityController {

    private final EligibilityService eligibility;

    public EligibilityController(EligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('eligibility.check')")
    @Operation(summary = "Assess a customer against a product",
            description = "Runs the configured eligibility rules and, if they pass, sizes the "
                    + "loan against every configured limit. Returns the criteria and their "
                    + "results either way, so a decline can be explained. Nothing here is "
                    + "hard-coded: every threshold comes from product or rule configuration.")
    public ApiResponse<EligibilityResponse> check(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody EligibilityRequest request) {
        return ApiResponse.success(
                eligibility.check(UUID.fromString(jwt.getSubject()), request));
    }
}
