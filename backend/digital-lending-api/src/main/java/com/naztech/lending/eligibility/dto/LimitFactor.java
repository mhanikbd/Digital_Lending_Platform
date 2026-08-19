package com.naztech.lending.eligibility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One cap considered by the amount engine, and why it came out where it did.
 *
 * <p>The specification asks that the engine "explain why a limit was produced",
 * which means every input is reported and not only the one that won. A customer
 * told they may borrow thirty thousand when the product advertises fifty is
 * owed the reason, and a banker asked for it should not have to reconstruct the
 * arithmetic.
 *
 * @param amount      the cap, or null when this factor is not configured and so
 *                    caps nothing - which is different from a cap of zero
 * @param binding     whether this is the factor that decided the final amount
 * @param explanation how the figure was arrived at, in words
 */
@Schema(description = "One limit considered when sizing the loan")
public record LimitFactor(
        @Schema(example = "INCOME_MULTIPLE") String code,
        @Schema(example = "Income based limit") String name,
        @Schema(example = "40000.00") BigDecimal amount,
        boolean binding,
        @Schema(example = "10 times a declared monthly income of 4,000.00")
        String explanation) {

    public static LimitFactor of(String code, String name, BigDecimal amount, String explanation) {
        return new LimitFactor(code, name, amount, false, explanation);
    }

    /** Not configured for this product version, so it constrains nothing. */
    public static LimitFactor notConfigured(String code, String name, String explanation) {
        return new LimitFactor(code, name, null, false, explanation);
    }

    public LimitFactor asBinding() {
        return new LimitFactor(code, name, amount, true, explanation);
    }

    /** True when this factor actually restricts anything. */
    public boolean applies() {
        return amount != null;
    }
}
