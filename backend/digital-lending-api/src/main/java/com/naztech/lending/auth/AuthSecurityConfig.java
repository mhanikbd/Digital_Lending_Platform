package com.naztech.lending.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The cryptographic parts of authentication.
 *
 * <p>Signing is symmetric while the platform is one deployable: nothing outside
 * this service needs to verify a token without also being able to mint one.
 * Splitting the API into services is the point at which this becomes RS256 with
 * a published JWKS, and only these two beans change.
 */
@Configuration
public class AuthSecurityConfig {

    /**
     * Cost 12 rather than the Spring default of 10. Roughly a quarter of a
     * second per verification on current hardware, which is negligible for one
     * sign-in and expensive for anyone working through a stolen hash dump.
     */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        return NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKey signingKey(AuthProperties properties) {
        byte[] material = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (material.length < 32) {
            // HS256 with a key shorter than its own digest is a real weakness,
            // and a misconfiguration that must not start silently.
            throw new IllegalStateException(
                    "dlp.auth.jwt.secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(material, "HmacSHA256");
    }
}
