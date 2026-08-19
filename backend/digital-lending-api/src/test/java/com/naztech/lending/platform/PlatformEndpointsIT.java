package com.naztech.lending.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.common.correlation.CorrelationId;
import com.naztech.lending.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end check of the Milestone 1 vertical: an HTTP client reaches Spring
 * Boot, which in turn reaches PostgreSQL, Redis and object storage.
 */
class PlatformEndpointsIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void reportsEveryInfrastructureDependencyAsReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/platform/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"status\":\"UP\"")
                .contains("\"name\":\"database\"")
                .contains("\"name\":\"cache\"")
                .contains("\"name\":\"objectStorage\"");
    }

    @Test
    void exposesEnvironmentIdentityWithoutACredential() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/platform/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"application\":\"digital-lending-api\"")
                .contains("\"apiVersion\":\"v1\"")
                .contains("\"environment\":\"test\"");
    }

    @Test
    void echoesTheCorrelationIdSuppliedByTheCaller() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CorrelationId.HEADER, "portal-session-91b2c7de");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/platform/info", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(CorrelationId.HEADER))
                .isEqualTo("portal-session-91b2c7de");
        assertThat(response.getBody()).contains("\"correlationId\":\"portal-session-91b2c7de\"");
    }

    @Test
    void closesEveryOtherEndpointUntilAuthenticationIsImplemented() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/customers", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void publishesTheOpenApiDocumentForClientGeneration() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Digital Lending Platform API")
                .contains("/api/v1/platform/health");
    }
}
