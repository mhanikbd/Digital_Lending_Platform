package com.naztech.lending.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins permitted to call the API. Kept in configuration rather than
 * code so each environment can be locked to its own portal host.
 *
 * @param allowedOrigins exact origins, never a wildcard when credentials are used
 * @param allowedMethods HTTP methods the portal may issue
 * @param maxAgeSeconds  how long a browser may cache the preflight result
 */
@ConfigurationProperties(prefix = "dlp.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        long maxAgeSeconds) {
}
