package com.naztech.lending.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.platform.commons.util.AnnotationUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Guards the Docker skip condition itself.
 *
 * <p>A bare {@code @EnabledIf} on {@link IntegrationTestBase} was silently
 * ineffective: the annotation is not {@code @Inherited}, so every integration
 * test ran regardless and failed on container startup instead of being skipped.
 * These tests need no Docker, so they catch that on any machine.
 */
class IntegrationTestBaseAnnotationTest {

    /** Stands in for a real integration test without needing one to be loaded. */
    static class SampleIntegrationTest extends IntegrationTestBase {
    }

    @Test
    void subclassesOfTheBaseResolveTheDockerCondition() {
        assertThat(AnnotationUtils.findAnnotation(SampleIntegrationTest.class, EnabledIf.class))
                .as("a subclass must inherit the Docker guard, or it will fail instead of skipping")
                .isPresent()
                .get()
                .satisfies(condition -> {
                    assertThat(condition.value())
                            .isEqualTo("com.naztech.lending.support.DockerAvailability#isAvailable");
                    assertThat(condition.disabledReason()).isNotBlank();
                });
    }

    @Test
    void everyIntegrationTestExtendsTheBase() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*IT$")));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.naztech.lending");
        assertThat(candidates)
                .as("the scan should find the integration tests; an empty result means it is not checking anything")
                .isNotEmpty();

        Set<String> notExtendingBase = candidates.stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(className -> !extendsIntegrationTestBase(className))
                .collect(Collectors.toSet());

        assertThat(notExtendingBase)
                .as("an integration test that does not extend IntegrationTestBase misses the Docker guard")
                .isEmpty();
    }

    private boolean extendsIntegrationTestBase(String className) {
        try {
            return IntegrationTestBase.class.isAssignableFrom(Class.forName(className));
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
