package com.naztech.lending.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * The authoritative quotation.
 *
 * <p>Everything §20 asks for, and the two figures customers most often get
 * wrong: what actually reaches their account, and what the loan costs in total.
 * A processing fee taken at disbursement means the amount borrowed and the
 * amount received are different numbers, and saying so plainly here is cheaper
 * than a complaint later.
 *
 * <p>Every decimal is serialised as a JSON string. Parsing them with
 * {@code parseFloat} will lose paisa; use a decimal library.
 */
@Schema(description = "An authoritative loan quotation produced by the backend")
public record LoanQuote(

        @Schema(example = "ELOAN") String productCode,
        @Schema(example = "e-Loan") String productName,
        @Schema(description = "The version these terms come from", example = "1") int productVersion,
        @Schema(example = "BDT") String currency,

        @Schema(example = "35000.00") BigDecimal principal,
        @Schema(example = "12") int tenureMonths,
        @Schema(example = "12") int instalments,
        @Schema(example = "MONTHLY") String repaymentFrequency,

        @Schema(description = "Percent per annum", example = "9.000000") BigDecimal interestRate,
        @Schema(example = "REDUCING_BALANCE") String interestMethod,
        @Schema(description = "True when the rate was negotiated rather than published")
        boolean rateNegotiated,

        @Schema(description = "The instalment; the last one may differ by a few paisa",
                example = "3062.30") BigDecimal instalment,
        @Schema(example = "1747.60") BigDecimal totalInterest,

        @Schema(description = "All fees excluding VAT", example = "525.00") BigDecimal totalFees,
        @Schema(example = "52.50") BigDecimal totalVat,

        @Schema(description = "Principal, interest, fees and VAT together",
                example = "37325.10") BigDecimal totalPayable,
        @Schema(description = "What reaches the customer's account after fees taken at "
                + "disbursement", example = "34422.50") BigDecimal netDisbursement,

        List<FeeLine> fees,
        List<InstalmentLine> schedule) {
}
