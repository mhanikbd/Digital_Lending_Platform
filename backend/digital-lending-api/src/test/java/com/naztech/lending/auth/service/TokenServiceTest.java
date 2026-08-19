package com.naztech.lending.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naztech.lending.auth.AuthProperties;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserType;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Tokens, verified by actually decoding them rather than by inspecting strings.
 *
 * <p>A round trip through a real encoder and decoder is the only test that says
 * anything useful here: it proves the claims survive, the signature verifies,
 * and a token signed with a different key does not.
 */
class TokenServiceTest {

    private static final String SECRET = "test-signing-key-that-is-long-enough-32";
    private static final String OTHER_SECRET = "a-completely-different-key-also-long-32";
    /**
     * The real clock, to whole seconds.
     *
     * <p>A fixed literal here was a time bomb: the decoder validates expiry
     * against the actual current time, so a token minted at a hard-coded instant
     * decodes fine on the day it is written and starts failing fifteen minutes
     * later, for good. Truncated to seconds because a JWT records exp and iat to
     * the second, so an untruncated instant would not compare equal.
     */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final List<String> ROLES = List.of("BM");
    private static final List<String> PERMISSIONS = List.of("system.health.view");

    private final AuthProperties properties = new AuthProperties(
            new AuthProperties.Jwt(SECRET, "digital-lending-platform", Duration.ofMinutes(15)),
            new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
            new AuthProperties.Otp(6, Duration.ofMinutes(5), 5, false),
            new AuthProperties.Session(Duration.ofDays(30)));

    private final TokenService tokenService =
            new TokenService(encoderFor(SECRET), properties);

    @Test
    void anIssuedTokenCarriesTheIdentityAndVerifies() {
        UserAccount user = new UserAccount(UserType.BANK_USER, "EMP-10001", "Test Officer");

        String token = tokenService.issueAccessToken(user, ROLES, PERMISSIONS, NOW);
        Jwt decoded = decoderFor(SECRET).decode(token);

        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsString("username")).isEqualTo("EMP-10001");
        assertThat(decoded.getClaimAsString("userType")).isEqualTo("BANK_USER");
        // Read as a raw claim, not via getIssuer(): iss is a StringOrURI in the
        // spec and ours is deliberately a plain name, which getIssuer() would
        // try and fail to parse as a URL.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("digital-lending-platform");
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void theTokenCarriesTheAuthorityItsHolderHadWhenItWasIssued() {
        UserAccount user = new UserAccount(UserType.BANK_USER, "EMP-10001", "Test Officer");

        Jwt decoded = decoderFor(SECRET).decode(
                tokenService.issueAccessToken(user, ROLES, PERMISSIONS, NOW));

        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("BM");
        assertThat(decoded.getClaimAsStringList("perms")).containsExactly("system.health.view");
    }

    @Test
    void authorityIsFrozenIntoTheTokenAtIssue() {
        UserAccount user = new UserAccount(UserType.BANK_USER, "EMP-10001", "Test Officer");

        String issuedWhenPermitted = tokenService.issueAccessToken(
                user, ROLES, List.of("admin.role.view"), NOW);
        String issuedAfterRevocation = tokenService.issueAccessToken(user, ROLES, List.of(), NOW);

        // The older token keeps the permission until it expires. That is the
        // trade a stateless token makes, and the reason the lifetime is short.
        assertThat(decoderFor(SECRET).decode(issuedWhenPermitted).getClaimAsStringList("perms"))
                .containsExactly("admin.role.view");
        assertThat(decoderFor(SECRET).decode(issuedAfterRevocation).getClaimAsStringList("perms"))
                .isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        UserAccount user = new UserAccount(UserType.BANK_USER, "EMP-10001", "Test Officer");
        String foreign = new TokenService(encoderFor(OTHER_SECRET), properties)
                .issueAccessToken(user, ROLES, PERMISSIONS, NOW);

        JwtDecoder ours = decoderFor(SECRET);

        assertThatThrownBy(() -> ours.decode(foreign))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    @Test
    void anExpiredTokenIsRejected() {
        UserAccount user = new UserAccount(UserType.BANK_USER, "EMP-10001", "Test Officer");
        String stale = tokenService.issueAccessToken(user, ROLES, PERMISSIONS, Instant.now().minus(Duration.ofHours(2)));

        assertThatThrownBy(() -> decoderFor(SECRET).decode(stale))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void refreshTokensAreUnguessableAndNeverRepeat() {
        String first = tokenService.generateRefreshToken();
        String second = tokenService.generateRefreshToken();

        assertThat(first).isNotEqualTo(second);
        // 32 bytes, url-safe base64, unpadded.
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void hashingIsStableAndProducesTheColumnWidthTheSchemaDeclares() {
        String token = tokenService.generateRefreshToken();

        assertThat(tokenService.hash(token))
                .isEqualTo(tokenService.hash(token))
                .hasSize(64)
                .matches("[0-9a-f]+");
        assertThat(tokenService.hash(token)).isNotEqualTo(tokenService.hash(token + "x"));
    }

    private static NimbusJwtEncoder encoderFor(String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(keyFor(secret)));
    }

    private static JwtDecoder decoderFor(String secret) {
        return NimbusJwtDecoder.withSecretKey(keyFor(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKeySpec keyFor(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
