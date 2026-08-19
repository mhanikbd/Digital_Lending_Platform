package com.naztech.lending.common.correlation;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Correlation identifier shared by log lines, API responses and outbound
 * integration calls so a single customer action can be traced end to end.
 */
public final class CorrelationId {

    /** Inbound/outbound HTTP header carrying the correlation identifier. */
    public static final String HEADER = "X-Correlation-Id";

    /** MDC key referenced by {@code logback-spring.xml}. */
    public static final String MDC_KEY = "correlationId";

    /**
     * Client-supplied values are echoed into logs, so they are accepted only when
     * they are short and free of control characters (log-injection guard).
     */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** Returns the supplied value when it is safe to propagate, otherwise a fresh one. */
    public static String sanitize(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches() ? candidate : generate();
    }

    /** Correlation id bound to the current thread, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void bind(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
