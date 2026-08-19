package com.naztech.lending.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.support.IntegrationTestBase;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Verifies the Spring Boot to Redis leg of the platform, including the
 * expiry behaviour that OTP challenges and rate-limit counters will depend on.
 */
class CacheConnectivityIT extends IntegrationTestBase {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void storesAndReadsBackAValue() {
        redisTemplate.opsForValue().set("dlp:test:greeting", "hello", Duration.ofMinutes(1));

        assertThat(redisTemplate.opsForValue().get("dlp:test:greeting")).isEqualTo("hello");
    }

    @Test
    void honoursTheTimeToLiveUsedForShortLivedState() {
        redisTemplate.opsForValue().set("dlp:test:otp", "123456", Duration.ofSeconds(30));

        Long ttl = redisTemplate.getExpire("dlp:test:otp");

        assertThat(ttl).isPositive().isLessThanOrEqualTo(30L);
    }

    @Test
    void serialisesStructuredValuesAsJsonRatherThanJavaSerialisation() {
        redisTemplate.opsForValue().set("dlp:test:structured", Map.of("productCode", "ELOAN"));

        Object readBack = redisTemplate.opsForValue().get("dlp:test:structured");
        // Read the same key through a plain string template to see what actually
        // landed in Redis: JDK serialisation would produce unreadable binary.
        String stored = stringRedisTemplate.opsForValue().get("dlp:test:structured");

        assertThat(readBack).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) readBack).get("productCode")).isEqualTo("ELOAN");
        assertThat(stored).isEqualTo("{\"productCode\":\"ELOAN\"}");
    }
}
