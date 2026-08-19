package com.naztech.lending.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.common.correlation.CorrelationId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @AfterEach
    void clearContext() {
        CorrelationId.clear();
    }

    @Test
    void successCarriesPayloadAndNoError() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void failureCarriesErrorAndNoPayload() {
        ApiResponse<Void> response = ApiResponse.failure(ApiError.of("VALIDATION_FAILED", "bad request"));

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.error().violations()).isEmpty();
    }

    @Test
    void stampsTheCorrelationIdBoundToTheCurrentRequest() {
        CorrelationId.bind("req-0000000000000042");

        assertThat(ApiResponse.success("x").correlationId()).isEqualTo("req-0000000000000042");
        assertThat(ApiResponse.failure(ApiError.of("X", "y")).correlationId())
                .isEqualTo("req-0000000000000042");
    }

    @Test
    void violationsAreDefensivelyCopied() {
        List<ApiError.FieldViolation> mutable = new java.util.ArrayList<>();
        mutable.add(new ApiError.FieldViolation("amount", "must be positive"));

        ApiError error = ApiError.of("VALIDATION_FAILED", "bad request", mutable);
        mutable.clear();

        assertThat(error.violations()).hasSize(1);
    }
}
