package com.naztech.lending.rules.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The facts a rule run is decided on.
 *
 * <p>Assembled once, before any rule is read, and then immutable. That matters
 * for more than tidiness: the snapshot written to the audit record has to be the
 * same values the rules actually saw, and it cannot be if a rule can go and
 * fetch something for itself half way through.
 *
 * <p>An absent attribute is not the same as a null one. Absent means no module
 * could supply it; null means the customer has not declared it. Both fail a rule
 * that tests them, but they are reported differently.
 */
public final class RuleContext {

    private final Map<String, Object> values;

    private RuleContext(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean has(String attributeCode) {
        return values.containsKey(attributeCode);
    }

    public Optional<Object> value(String attributeCode) {
        return Optional.ofNullable(values.get(attributeCode));
    }

    /**
     * The value as it is recorded in the audit detail.
     *
     * <p>{@code null} when nothing could be supplied, which reads as "unknown"
     * in the record rather than as a value that happened to be empty.
     */
    public String render(String attributeCode) {
        Object value = values.get(attributeCode);
        return value == null ? null : String.valueOf(value);
    }

    /** Every fact, rendered, for the JSON snapshot kept with the decision. */
    public Map<String, String> snapshot() {
        Map<String, String> rendered = new LinkedHashMap<>();
        values.forEach((code, value) -> rendered.put(code, value == null ? null : String.valueOf(value)));
        return rendered;
    }

    public static final class Builder {

        private final Map<String, Object> values = new LinkedHashMap<>();

        /**
         * Records a fact. A null value is still recorded: "we looked and the
         * customer has not told us" is itself something the audit should hold.
         */
        public Builder put(String attributeCode, Object value) {
            values.put(attributeCode, value);
            return this;
        }

        public Builder number(String attributeCode, BigDecimal value) {
            return put(attributeCode, value);
        }

        public Builder number(String attributeCode, Integer value) {
            return put(attributeCode, value == null ? null : BigDecimal.valueOf(value));
        }

        public Builder text(String attributeCode, Object value) {
            return put(attributeCode, value == null ? null : String.valueOf(value));
        }

        public Builder flag(String attributeCode, boolean value) {
            return put(attributeCode, value);
        }

        public Builder date(String attributeCode, LocalDate value) {
            return put(attributeCode, value);
        }

        public RuleContext build() {
            return new RuleContext(new LinkedHashMap<>(values));
        }
    }
}
