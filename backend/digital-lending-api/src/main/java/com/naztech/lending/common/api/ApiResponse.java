package com.naztech.lending.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.naztech.lending.common.correlation.CorrelationId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Envelope used by every {@code /api/**} endpoint so clients can rely on one
 * response shape for both success and failure.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public record ApiResponse<T>(
        @Schema(description = "True when the request succeeded") boolean success,
        @Schema(description = "Payload, present when success is true") T data,
        @Schema(description = "Error detail, present when success is false") ApiError error,
        @Schema(description = "Correlation id, also returned as the X-Correlation-Id header")
        String correlationId,
        @Schema(description = "Server timestamp in UTC") Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, CorrelationId.current(), Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, error, CorrelationId.current(), Instant.now());
    }
}
