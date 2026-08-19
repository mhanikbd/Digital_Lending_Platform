package com.naztech.lending.security;

import com.naztech.lending.config.CorsProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Baseline security posture for the platform.
 *
 * <p>Milestone 1 establishes the shape only: the API is stateless, closed by
 * default, and returns the standard error envelope. Customer PIN/OTP login,
 * bank-user MFA, JWT issuance and database-driven RBAC arrive in Milestone 5 and
 * plug into {@code anyRequest().authenticated()} without reshaping this class.
 *
 * <p>Actuator runs on its own management port that is never published outside the
 * container network, which is why it gets a permissive chain of its own.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Paths that must stay reachable without a credential, in every environment. */
    private static final String[] BASELINE_PUBLIC_PATHS = {
            "/api/v1/auth/bank/login",
            "/api/v1/auth/bank/mfa",
            "/api/v1/auth/customer/otp",
            "/api/v1/auth/customer/otp/verify",
            "/api/v1/auth/customer/login",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/logout",
            "/api/v1/platform/health",
            "/api/v1/platform/info",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    /**
     * The paths open without a credential: the baseline, plus whatever a
     * profile-scoped module contributes.
     *
     * <p>Contributions are logged with their reason. An endpoint that is open
     * because of a profile should be visible in the startup log of the
     * environment that opened it, and absent from every other one.
     */
    private String[] publicPaths(List<PublicEndpoints> contributors) {
        List<String> paths = new java.util.ArrayList<>(List.of(BASELINE_PUBLIC_PATHS));
        for (PublicEndpoints contributor : contributors) {
            paths.addAll(contributor.paths());
            log.info("Permitting {} without authentication: {}",
                    contributor.paths(), contributor.reason());
        }
        return paths.toArray(String[]::new);
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               CorsConfigurationSource corsConfigurationSource,
                                               JwtAuthenticationConverter jwtAuthenticationConverter,
                                               RestAuthenticationEntryPoint authenticationEntryPoint,
                                               RestAccessDeniedHandler accessDeniedHandler,
                                               List<PublicEndpoints> publicEndpoints) throws Exception {
        return http
                // No cookies or server-side sessions are used, so CSRF tokens add nothing.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(publicPaths(publicEndpoints)).permitAll()
                        // Closed by default: a new endpoint is unreachable until its
                        // authorisation rule is added deliberately.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(java.util.List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-Correlation-Id",
                "Idempotency-Key"));
        configuration.setExposedHeaders(java.util.List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(properties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
