package com.naztech.lending.support;

import org.testcontainers.DockerClientFactory;

/**
 * Guard used by {@link IntegrationTestBase}.
 *
 * <p>Integration tests need a Docker daemon. Where one is not present the tests
 * are skipped with a reason rather than failing, so a developer without Docker
 * still gets a meaningful unit test run, while CI, which always has Docker,
 * executes the full suite.
 */
public final class DockerAvailability {

    private DockerAvailability() {
    }

    public static boolean isAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }
}
