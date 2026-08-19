package com.naztech.lending.auth.service;

import com.naztech.lending.auth.AuthProperties;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * One-time passcodes.
 *
 * <p>Challenges live in Redis because they are short-lived state, which is the
 * only thing this platform keeps there. Losing them to a cache flush costs a
 * customer one retry; it never loses anything authoritative.
 *
 * <p>The code itself is stored as a SHA-256 hash and is never written to a log,
 * which the platform security rules forbid outright. Delivery is not wired yet:
 * the SMS provider abstraction arrives with the integration milestones, so until
 * then a development environment may set {@code exposeInResponse} to complete
 * the journey without a gateway.
 */
@Service
public class OtpService {

    private static final String KEY_PREFIX = "auth:otp:";
    private static final String FIELD_SUBJECT = "subject";
    private static final String FIELD_PURPOSE = "purpose";
    private static final String FIELD_CODE_HASH = "codeHash";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final RedisTemplate<String, Object> redis;
    private final TokenService tokenService;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public OtpService(RedisTemplate<String, Object> redis,
                      TokenService tokenService,
                      AuthProperties properties) {
        this.redis = redis;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /** What the caller gets back. The code is only populated in development. */
    public record Challenge(String challengeId, long expiresInSeconds, String developmentCode) {
    }

    /** Why a challenge was raised, so a code for one purpose cannot serve another. */
    public enum Purpose {
        DEVICE_BINDING,
        BANK_USER_MFA
    }

    public Challenge issue(String subject, Purpose purpose) {
        String challengeId = UUID.randomUUID().toString();
        String code = generateCode();

        redis.opsForHash().putAll(KEY_PREFIX + challengeId, Map.of(
                FIELD_SUBJECT, subject,
                FIELD_PURPOSE, purpose.name(),
                FIELD_CODE_HASH, tokenService.hash(code),
                FIELD_ATTEMPTS, "0"));
        redis.expire(KEY_PREFIX + challengeId, properties.otp().ttl());

        return new Challenge(
                challengeId,
                properties.otp().ttl().toSeconds(),
                properties.otp().exposeInResponse() ? code : null);
    }

    /**
     * Checks a code against a challenge.
     *
     * <p>A correct code burns the challenge, so it cannot be replayed. Too many
     * wrong codes burn it too, so a challenge cannot be brute forced within its
     * own lifetime.
     */
    public Verification verify(String challengeId, String code, String expectedSubject, Purpose purpose) {
        String key = KEY_PREFIX + challengeId;
        Optional<String> storedHash = readField(key, FIELD_CODE_HASH);
        if (storedHash.isEmpty()) {
            return Verification.EXPIRED;
        }

        boolean subjectMatches = readField(key, FIELD_SUBJECT).filter(expectedSubject::equals).isPresent();
        boolean purposeMatches = readField(key, FIELD_PURPOSE).filter(purpose.name()::equals).isPresent();
        if (!subjectMatches || !purposeMatches) {
            // A challenge raised for someone else, or for another purpose, is
            // not a wrong code; it is not this caller's challenge at all.
            return Verification.EXPIRED;
        }

        if (storedHash.get().equals(tokenService.hash(code))) {
            redis.delete(key);
            return Verification.VERIFIED;
        }

        long attempts = redis.opsForHash().increment(key, FIELD_ATTEMPTS, 1L);
        if (attempts >= properties.otp().maxAttempts()) {
            redis.delete(key);
            return Verification.ATTEMPTS_EXHAUSTED;
        }
        return Verification.INCORRECT;
    }

    public enum Verification {
        VERIFIED,
        INCORRECT,
        ATTEMPTS_EXHAUSTED,
        EXPIRED
    }

    /**
     * The subject a challenge was raised for, so a caller can be resolved from
     * the challenge id alone rather than by searching for whom it might belong to.
     */
    public Optional<String> subjectOf(String challengeId) {
        return readField(KEY_PREFIX + challengeId, FIELD_SUBJECT);
    }

    private Optional<String> readField(String key, String field) {
        Object value = redis.opsForHash().get(key, field);
        return Optional.ofNullable(value).map(Object::toString);
    }

    private String generateCode() {
        int digits = properties.otp().length();
        StringBuilder code = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
