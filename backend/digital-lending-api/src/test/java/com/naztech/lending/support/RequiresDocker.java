package com.naztech.lending.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Marks a test that needs a Docker daemon, and skips it with a reason when there
 * is none.
 *
 * <p>This exists because {@link EnabledIf} is not {@code @Inherited}: placing it
 * directly on an abstract base class has no effect on the concrete subclasses,
 * which silently run anyway and fail on container startup. Meta-annotating it
 * here, on an {@code @Inherited} annotation, makes it apply to everything that
 * extends {@link IntegrationTestBase}.
 *
 * <p>{@link IntegrationTestBaseAnnotationTest} guards that behaviour, so the
 * regression cannot come back unnoticed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@EnabledIf(value = "com.naztech.lending.support.DockerAvailability#isAvailable",
        disabledReason = "Requires a running Docker daemon")
public @interface RequiresDocker {
}
