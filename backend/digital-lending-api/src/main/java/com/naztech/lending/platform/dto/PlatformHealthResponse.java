package com.naztech.lending.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Aggregated connectivity of the platform, used by the bank portal system page
 * to prove the Web to Spring Boot to PostgreSQL/Redis/MinIO chain end to end.
 *
 * @param status     {@code UP} only when every component is up
 * @param components per-dependency detail
 */
@Schema(description = "Aggregated platform connectivity")
public record PlatformHealthResponse(
        @Schema(example = "UP", allowableValues = {"UP", "DOWN"}) String status,
        List<ComponentStatus> components) {

    public static PlatformHealthResponse from(List<ComponentStatus> components) {
        boolean allUp = components.stream().allMatch(ComponentStatus::isUp);
        return new PlatformHealthResponse(
                allUp ? ComponentStatus.UP : ComponentStatus.DOWN, List.copyOf(components));
    }
}
