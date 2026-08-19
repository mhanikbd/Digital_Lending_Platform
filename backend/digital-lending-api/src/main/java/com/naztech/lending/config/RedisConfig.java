package com.naztech.lending.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis holds short-lived state only: OTP challenges, login attempt counters,
 * rate-limit buckets and idempotency keys. Nothing here is authoritative; the
 * authoritative record always lives in PostgreSQL.
 *
 * <p>Values are serialised as JSON. The Spring Data default is JDK
 * serialisation, which is both opaque and a deserialisation risk.
 *
 * <p>Jackson default typing is deliberately left off, so no {@code @class}
 * property is written and a stored value cannot force instantiation of an
 * arbitrary class when it is read back. The consequence is that a structured
 * value returns as a {@code Map}, not as the type that was written: read it back
 * by naming the target type, never by casting the result. {@code RedisConfigTest}
 * pins this behaviour.
 */
@Configuration
public class RedisConfig {

    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
