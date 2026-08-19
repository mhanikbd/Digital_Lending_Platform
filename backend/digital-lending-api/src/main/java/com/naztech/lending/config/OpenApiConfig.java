package com.naztech.lending.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the OpenAPI document that the web portal and mobile clients build against. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI digitalLendingOpenApi(@Value("${spring.application.name}") String applicationName,
                                  @Value("${dlp.api.version}") String apiVersion) {
        return new OpenAPI().info(new Info()
                .title("Digital Lending Platform API")
                .version(apiVersion)
                .description("""
                        Authoritative API for the digital lending and account opening platform.

                        Conventions:
                        - every endpoint is versioned under /api/v1
                        - every response uses the ApiResponse envelope
                        - monetary and rate values are serialised as decimal strings
                        - every request and response carries an X-Correlation-Id header
                        """)
                .contact(new Contact().name(applicationName))
                .license(new License().name("Proprietary")));
    }
}
