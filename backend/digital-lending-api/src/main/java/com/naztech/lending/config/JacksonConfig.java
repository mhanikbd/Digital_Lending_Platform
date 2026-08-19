package com.naztech.lending.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON conventions for the whole API.
 *
 * <p>Monetary and rate values are {@link BigDecimal} in Java and NUMERIC in
 * PostgreSQL. They are serialised as JSON <em>strings</em>, not numbers: a
 * JavaScript client parses a JSON number into a 64-bit float, which silently
 * loses the precision the platform is required to preserve. Clients must parse
 * these values with a decimal library, never with {@code parseFloat}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer decimalSafeJsonCustomizer() {
        SimpleModule module = new SimpleModule("decimal-safe");
        module.addSerializer(BigDecimal.class, new PlainStringBigDecimalSerializer());
        return builder -> builder.modulesToInstall(module);
    }

    /** Writes a BigDecimal in plain (non-scientific) notation, preserving its scale. */
    static class PlainStringBigDecimalSerializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(value.toPlainString());
        }
    }
}
