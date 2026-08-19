package com.naztech.lending.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naztech.lending.common.correlation.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercises the advice through a real dispatch, including the correlation filter,
 * so the envelope is verified exactly as a client would receive it.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @Test
    void reportsEveryFieldViolationForAnInvalidBody() throws Exception {
        mockMvc.perform(post("/probe/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"\",\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations.length()").value(2))
                .andExpect(jsonPath("$.error.violations[0].field").value("amount"))
                .andExpect(jsonPath("$.error.violations[1].field").value("purpose"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void mapsBusinessRuleViolationToUnprocessableEntity() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value("Tenure exceeds the product maximum"));
    }

    @Test
    void mapsMissingResourceToNotFound() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message")
                        .value(Matchers.containsString("Loan application")));
    }

    @Test
    void neverLeaksInternalFailureDetailToTheClient() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("jdbc:postgresql"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    @Test
    void mapsAnUnparseablePathVariableToValidationFailure() throws Exception {
        mockMvc.perform(get("/probe/application/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("id"));
    }

    @Test
    void mapsAnUnsupportedMethodToMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/probe/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void mapsAnUnreadableBodyToMalformedRequest() throws Exception {
        mockMvc.perform(post("/probe/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    record ApplyRequest(@NotBlank String purpose, @Positive BigDecimal amount) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/apply")
        String apply(@Valid @RequestBody ApplyRequest request) {
            return "accepted";
        }

        @GetMapping("/probe/business")
        String business() {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Tenure exceeds the product maximum");
        }

        @GetMapping("/probe/missing")
        String missing() {
            throw new ResourceNotFoundException("Loan application", "APP-2026-000123");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("jdbc:postgresql://internal-host/digital_lending timed out");
        }

        @GetMapping("/probe/application/{id}")
        String byId(@PathVariable long id) {
            return String.valueOf(id);
        }
    }
}
