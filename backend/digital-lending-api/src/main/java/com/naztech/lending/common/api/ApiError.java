package com.naztech.lending.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Machine-readable error detail. {@code code} is stable and safe to branch on in
 * clients; {@code message} is for humans and may change.
 */
@Schema(description = "Error detail returned when a request fails")
public record ApiError(
        @Schema(description = "Stable error code", example = "VALIDATION_FAILED") String code,
        @Schema(description = "Human readable message") String message,
        @Schema(description = "Field level violations, present for validation failures")
        List<FieldViolation> violations) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }

    public static ApiError of(String code, String message, List<FieldViolation> violations) {
        return new ApiError(code, message, violations == null ? List.of() : List.copyOf(violations));
    }

    /**
     * A single field-level validation failure. The rejected value is deliberately
     * not echoed back: request bodies in this platform routinely carry PII.
     */
    @Schema(description = "Field level validation failure")
    public record FieldViolation(
            @Schema(example = "amount") String field,
            @Schema(example = "must be greater than 0") String message) {
    }
}
