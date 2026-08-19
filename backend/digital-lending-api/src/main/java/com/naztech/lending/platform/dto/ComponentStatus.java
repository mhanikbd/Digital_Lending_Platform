package com.naztech.lending.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reachability of one infrastructure dependency.
 *
 * <p>Detail is deliberately coarse. This endpoint is reachable without a
 * credential, so it must not disclose hostnames, driver versions or stack traces.
 *
 * @param name   dependency name, for example {@code database}
 * @param status {@code UP} or {@code DOWN}
 * @param detail short, non-sensitive explanation
 */
@Schema(description = "Reachability of a single infrastructure dependency")
public record ComponentStatus(
        @Schema(example = "database") String name,
        @Schema(example = "UP", allowableValues = {"UP", "DOWN"}) String status,
        @Schema(example = "reachable") String detail) {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    public static ComponentStatus up(String name) {
        return new ComponentStatus(name, UP, "reachable");
    }

    public static ComponentStatus down(String name, String detail) {
        return new ComponentStatus(name, DOWN, detail);
    }

    public boolean isUp() {
        return UP.equals(status);
    }
}
