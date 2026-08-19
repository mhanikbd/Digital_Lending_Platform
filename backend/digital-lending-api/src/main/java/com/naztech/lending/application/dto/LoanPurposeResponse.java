package com.naztech.lending.application.dto;

import com.naztech.lending.application.domain.LoanPurpose;
import io.swagger.v3.oas.annotations.media.Schema;

/** A purpose a customer may choose. */
@Schema(description = "A configured loan purpose")
public record LoanPurposeResponse(
        @Schema(example = "MEDICAL") String code,
        @Schema(example = "Medical") String name,
        @Schema(description = "The name in Bangla") String nameBn,
        @Schema(description = "Whether the applicant must say more") boolean requiresDetail) {

    public static LoanPurposeResponse from(LoanPurpose purpose) {
        return new LoanPurposeResponse(purpose.getCode(), purpose.getName(),
                purpose.getNameBn(), purpose.isRequiresDetail());
    }
}
