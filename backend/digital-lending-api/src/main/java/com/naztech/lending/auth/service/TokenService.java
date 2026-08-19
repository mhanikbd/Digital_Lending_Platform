package com.naztech.lending.auth.service;

import com.naztech.lending.auth.AuthProperties;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the two tokens a session is made of.
 *
 * <p>The access token is a signed JWT: self-describing, verifiable without a
 * database read, and therefore not revocable - which is why its lifetime is
 * measured in minutes. The refresh token is the opposite: opaque, meaningless
 * on its own, and only useful against a row in auth.t_session, which is what
 * makes a session revocable.
 *
 * <p>Only the SHA-256 of a refresh token is ever persisted. A reader of the
 * session table therefore cannot replay one.
 */
@Service
public class TokenService {

    /** 32 bytes of entropy, url-safe encoded. Long enough that guessing is not a threat model. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * Mints an access token for an identity.
     *
     * <p>The token carries the permission codes the holder had at the moment it
     * was issued, so an authorisation decision costs no database read. The price
     * is that a permission taken away is still honoured until the token expires,
     * which is one of the reasons the lifetime is measured in minutes rather
     * than hours. A revocation that must bite immediately revokes the session,
     * not the grant.
     */
    public String issueAccessToken(UserAccount user, List<String> roles,
                                   List<String> permissions, Instant now) {
        Instant expiresAt = now.plus(properties.jwt().accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("userType", user.getUserType().name())
                .claim("displayName", user.getDisplayName())
                .claim("roles", roles)
                .claim("perms", permissions)
                .build();
        JwsHeader header = JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** A fresh opaque refresh token. The caller stores only {@link #hash(String)}. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256, hex encoded, to match the CHAR(64) column in V2. */
    public String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the platform spec; absent means a broken JVM.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public long accessTtlSeconds() {
        return properties.jwt().accessTtl().toSeconds();
    }

    public long refreshTtlSeconds() {
        return properties.session().refreshTtl().toSeconds();
    }

    /** Seconds a caller may still use this session before signing in again. */
    public long remainingSeconds(UserSession session, Instant now) {
        long remaining = session.getExpiresAt().getEpochSecond() - now.getEpochSecond();
        return Math.max(remaining, 0);
    }
}
