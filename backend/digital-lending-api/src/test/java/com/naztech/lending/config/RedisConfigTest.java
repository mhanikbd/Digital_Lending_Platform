package com.naztech.lending.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Pins the Redis serialization contract.
 *
 * <p>Redis holds OTP challenges, login attempt counters, rate-limit buckets and
 * idempotency keys from Milestone 5 onwards, so how values are written matters
 * before anything writes them. These checks need no Redis instance, which is why
 * they live here rather than only in the Docker-gated integration test.
 */
@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private RedisTemplate<String, Object> template() {
        return new RedisConfig().redisTemplate(connectionFactory, new ObjectMapper());
    }

    @Test
    void neverUsesJavaSerialisation() {
        RedisTemplate<String, Object> template = template();

        // The Spring Data default is JDK serialisation: opaque on the wire and a
        // deserialisation gadget risk. Both value paths must be JSON instead.
        assertThat(template.getValueSerializer())
                .isNotInstanceOf(JdkSerializationRedisSerializer.class)
                .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer())
                .isNotInstanceOf(JdkSerializationRedisSerializer.class)
                .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
    }

    @Test
    void keysArePlainStringsSoTheyCanBeReadWithRedisCli() {
        RedisTemplate<String, Object> template = template();

        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void writesPlainJsonWithoutEmbeddedTypeMetadata() {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer =
                (RedisSerializer<Object>) template().getValueSerializer();

        String stored = new String(
                serializer.serialize(Map.of("productCode", "ELOAN")), StandardCharsets.UTF_8);

        // No "@class" property: default typing is deliberately not enabled, so a
        // value written by one version cannot force instantiation of an arbitrary
        // class when read by another.
        assertThat(stored).isEqualTo("{\"productCode\":\"ELOAN\"}");
    }

    @Test
    void readsStructuredValuesBackAsMapsRatherThanTheOriginalType() {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer =
                (RedisSerializer<Object>) template().getValueSerializer();

        Object readBack = serializer.deserialize(serializer.serialize(Map.of("otp", "123456")));

        // Consequence of leaving default typing off, and the reason later modules
        // must read through a typed helper that names the target class rather than
        // casting whatever comes back.
        assertThat(readBack).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) readBack).get("otp")).isEqualTo("123456");
    }
}
