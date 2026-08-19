package com.naztech.lending.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The platform's clock.
 *
 * <p>Injected rather than read from {@code Instant.now()} so that anything which
 * decides something on a date - eligibility on an age, a version's effective
 * window, an ageing bucket - can be tested at a chosen moment instead of only
 * ever at the moment the test happens to run.
 *
 * <p>The system default zone is deliberate: the platform runs in one country and
 * its business days, cut-offs and ageing are Dhaka's. Instants are stored in UTC
 * regardless, because the column is {@code TIMESTAMPTZ}.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
