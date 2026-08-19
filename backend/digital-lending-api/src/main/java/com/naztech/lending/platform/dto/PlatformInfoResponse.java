package com.naztech.lending.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Non-sensitive build and runtime identity, so a client can confirm which
 * environment and API version it is talking to.
 *
 * @param application application name
 * @param apiVersion  API contract version exposed under /api/{version}
 * @param environment active Spring profile
 * @param serverTime  authoritative server time in UTC
 */
@Schema(description = "Platform identity and server time")
public record PlatformInfoResponse(
        @Schema(example = "digital-lending-api") String application,
        @Schema(example = "v1") String apiVersion,
        @Schema(example = "docker") String environment,
        Instant serverTime) {
}
