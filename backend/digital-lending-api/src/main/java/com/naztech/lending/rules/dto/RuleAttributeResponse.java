package com.naztech.lending.rules.dto;

import com.naztech.lending.rules.domain.RuleAttribute;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One testable fact, and what may be asked of it.
 *
 * <p>The legal operators come with it so a configuration screen can offer the
 * right ones rather than offering all nine and letting somebody build a rule
 * that asks whether a KYC status is greater than another.
 */
@Schema(description = "An attribute a rule may test")
public record RuleAttributeResponse(
        @Schema(example = "customer.age") String code,
        @Schema(example = "Age in years") String name,
        String description,
        @Schema(example = "NUMBER") String dataType,
        @Schema(example = "CUSTOMER") String source,
        @Schema(description = "Operators this type accepts") List<String> operators) {

    public static RuleAttributeResponse from(RuleAttribute attribute) {
        return new RuleAttributeResponse(
                attribute.getCode(),
                attribute.getName(),
                attribute.getDescription(),
                attribute.getDataType().name(),
                attribute.getSource(),
                attribute.getDataType().supportedOperators().stream()
                        .map(Enum::name).sorted().toList());
    }
}
