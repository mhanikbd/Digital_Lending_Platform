package com.naztech.lending;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Digital Lending Platform backend.
 *
 * <p>The backend is a modular monolith: every domain module lives under
 * {@code com.naztech.lending.<module>} and owns its own PostgreSQL logical schema.
 * All authoritative business decisions (eligibility, limits, pricing, workflow
 * transitions, approval authority, classification) are made here, never in a client.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DigitalLendingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalLendingApplication.class, args);
    }
}
