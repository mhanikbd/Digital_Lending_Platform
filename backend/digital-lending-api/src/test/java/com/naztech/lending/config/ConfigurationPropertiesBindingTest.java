package com.naztech.lending.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.storage.ObjectStorageProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Binds the configuration records the way Spring does at startup.
 *
 * <p>A binding mistake does not fail at compile time, it fails when the context
 * loads, which until Docker is available is the one thing the test suite cannot
 * exercise. These checks cover the risky part: values that arrive from the
 * environment as a single comma-separated string but are declared as a list.
 */
class ConfigurationPropertiesBindingTest {

    private static <T> T bind(String prefix, Class<T> type, Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind(prefix, type)
                .orElseThrow(() -> new AssertionError("binding produced no value for " + prefix));
    }

    @Test
    void bindsCorsOriginsSuppliedAsOneCommaSeparatedEnvironmentVariable() {
        CorsProperties properties = bind("dlp.cors", CorsProperties.class, Map.of(
                "dlp.cors.allowed-origins", "http://localhost:3000,https://portal.bank.example",
                "dlp.cors.allowed-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS",
                "dlp.cors.max-age-seconds", "3600"));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:3000", "https://portal.bank.example");
        assertThat(properties.allowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(properties.maxAgeSeconds()).isEqualTo(3600L);
    }

    @Test
    void bindsASingleCorsOriginWithoutSplittingIt() {
        CorsProperties properties = bind("dlp.cors", CorsProperties.class, Map.of(
                "dlp.cors.allowed-origins", "http://localhost:3000",
                "dlp.cors.allowed-methods", "GET",
                "dlp.cors.max-age-seconds", "3600"));

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:3000");
    }

    @Test
    void bindsObjectStoragePropertiesIncludingTheBooleanFlag() {
        ObjectStorageProperties properties = bind("dlp.storage", ObjectStorageProperties.class, Map.of(
                "dlp.storage.endpoint", "http://minio:9000",
                "dlp.storage.access-key", "access",
                "dlp.storage.secret-key", "secret",
                "dlp.storage.bucket", "dlp-documents",
                "dlp.storage.auto-create-bucket", "false"));

        assertThat(properties.endpoint()).isEqualTo("http://minio:9000");
        assertThat(properties.bucket()).isEqualTo("dlp-documents");
        assertThat(properties.autoCreateBucket()).isFalse();
    }
}
