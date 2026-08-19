package com.naztech.lending.pricing.dto;

import com.naztech.lending.pricing.service.LoanCalculator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One row of the repayment schedule. */
@Schema(description = "One instalment")
public record InstalmentLine(
        @Schema(example = "1") int number,
        @Schema(example = "3062.75") BigDecimal amountDue,
        @Schema(example = "2800.25") BigDecimal principal,
        @Schema(example = "262.50") BigDecimal interest,
        @Schema(description = "Owed after this instalment; zero on the last",
                example = "32199.75") BigDecimal closingBalance) {

    public static InstalmentLine of(LoanCalculator.Instalment instalment) {
        return new InstalmentLine(instalment.number(), instalment.amountDue(),
                instalment.principal(), instalment.interest(), instalment.closingBalance());
    }
}
