package com.naztech.lending.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * One set of infrastructure containers shared by every integration test in the
 * build. They are started once on first use and torn down by Testcontainers when
 * the JVM exits, which keeps the suite fast enough to run on every commit.
 *
 * <p>Image tags are pinned and must stay in step with
 * {@code infrastructure/docker/docker-compose.yml}: tests are only meaningful if
 * they run against the versions the platform actually deploys.
 */
public final class PlatformContainers {

    public static final String POSTGRES_IMAGE = "postgres:17-alpine";
    public static final String REDIS_IMAGE = "redis:7-alpine";
    public static final String MINIO_IMAGE = "minio/minio:RELEASE.2025-09-07T16-13-09Z";

    private static final String MINIO_ACCESS_KEY = "test-access-key";
    private static final String MINIO_SECRET_KEY = "test-secret-key";

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName("digital_lending")
                    .withUsername("dlp_owner")
                    .withPassword("dlp_test_only");

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(6379);

    public static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                    .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
    }

    private PlatformContainers() {
    }

    public static String redisHost() {
        return REDIS.getHost();
    }

    public static int redisPort() {
        return REDIS.getMappedPort(6379);
    }

    public static String storageEndpoint() {
        return "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
    }

    public static String storageAccessKey() {
        return MINIO_ACCESS_KEY;
    }

    public static String storageSecretKey() {
        return MINIO_SECRET_KEY;
    }
}
