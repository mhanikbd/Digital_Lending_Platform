package com.naztech.lending.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything about authentication that a bank may want to tune per environment.
 *
 * <p>These are security parameters, not business rules, so they live in
 * configuration rather than in the product tables that Milestone 13 introduces.
 */
@Validated
@ConfigurationProperties(prefix = "dlp.auth")
public record AuthProperties(
        @NotNull Jwt jwt,
        @NotNull Lockout lockout,
        @NotNull Otp otp,
        @NotNull Session session) {

    /**
     * Access token signing. HMAC rather than RSA while the platform is one
     * deployable: there is no second party that needs to verify without the
     * signing key. Splitting the API is the point at which this becomes RS256
     * with a published JWKS.
     *
     * @param secret     signing key, at least 32 bytes; supplied by secret, never logged
     * @param issuer     iss claim, so a token from another environment is rejected
     * @param accessTtl  how long an access token is accepted; short, because it cannot be revoked
     */
    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTtl) {
    }

    /**
     * Brute-force control.
     *
     * @param maxFailedAttempts consecutive failures before the account locks
     * @param lockDuration      how long the lock is served before attempts are judged afresh
     */
    public record Lockout(
            @Min(1) int maxFailedAttempts,
            @NotNull Duration lockDuration) {
    }

    /**
     * One-time passcodes, held in Redis.
     *
     * @param length          digits in the code
     * @param ttl             how long a code stays valid
     * @param maxAttempts     verification attempts before the challenge is burnt
     * @param exposeInResponse development-only: returns the code in the API response so a
     *                         developer can complete the journey without an SMS gateway.
     *                         Must be false anywhere a real customer can reach.
     */
    public record Otp(
            @Min(4) int length,
            @NotNull Duration ttl,
            @Min(1) int maxAttempts,
            boolean exposeInResponse) {
    }

    /**
     * Refresh-token sessions.
     *
     * @param refreshTtl how long a session may be refreshed before signing in again
     */
    public record Session(@NotNull Duration refreshTtl) {
    }
}
