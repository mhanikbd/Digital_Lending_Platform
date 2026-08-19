package com.naztech.lending.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that exercise the application against real PostgreSQL,
 * Redis and object storage.
 *
 * <p>The Docker check is a JUnit condition rather than a Testcontainers one so
 * that {@link PlatformContainers} is never loaded, and therefore never attempts
 * to start anything, on a machine without a Docker daemon. It is applied through
 * {@link RequiresDocker} because a bare {@code @EnabledIf} on a base class is not
 * inherited by its subclasses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@RequiresDocker
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PlatformContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PlatformContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PlatformContainers.POSTGRES::getPassword);

        registry.add("spring.data.redis.host", PlatformContainers::redisHost);
        registry.add("spring.data.redis.port", PlatformContainers::redisPort);

        registry.add("dlp.storage.endpoint", PlatformContainers::storageEndpoint);
        registry.add("dlp.storage.access-key", PlatformContainers::storageAccessKey);
        registry.add("dlp.storage.secret-key", PlatformContainers::storageSecretKey);
    }
}
