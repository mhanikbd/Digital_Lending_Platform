package com.naztech.lending.platform;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.platform.dto.PlatformHealthResponse;
import com.naztech.lending.platform.dto.PlatformInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public platform endpoints used to verify an environment is wired correctly.
 *
 * <p>These are intentionally unauthenticated so that the bank portal can render a
 * system page before any user has signed in. They expose reachability only, never
 * hostnames, versions or configuration. When bank-user authentication lands in
 * Milestone 5 the system page moves behind an admin permission and these become
 * internal-only.
 */
@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Platform", description = "Environment identity and infrastructure connectivity")
public class PlatformController {

    private final PlatformHealthService healthService;
    private final String applicationName;
    private final String apiVersion;
    private final String environment;

    public PlatformController(PlatformHealthService healthService,
                              @Value("${spring.application.name}") String applicationName,
                              @Value("${dlp.api.version}") String apiVersion,
                              @Value("${dlp.environment}") String environment) {
        this.healthService = healthService;
        this.applicationName = applicationName;
        this.apiVersion = apiVersion;
        this.environment = environment;
    }

    @GetMapping("/health")
    @Operation(summary = "Infrastructure connectivity",
            description = "Reports whether PostgreSQL, Redis and object storage are reachable. "
                    + "This is a diagnostic view for the portal system page, not a probe: it "
                    + "answers with 200 and a per-component status even when a dependency is "
                    + "down, so an operator can see which one. Liveness and readiness probes "
                    + "use /actuator/health on the internal management port.")
    public ApiResponse<PlatformHealthResponse> health() {
        return ApiResponse.success(healthService.check());
    }

    @GetMapping("/info")
    @Operation(summary = "Platform identity",
            description = "Application name, API version, active environment and authoritative server time.")
    public ApiResponse<PlatformInfoResponse> info() {
        return ApiResponse.success(
                new PlatformInfoResponse(applicationName, apiVersion, environment, Instant.now()));
    }
}
