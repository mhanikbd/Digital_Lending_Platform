package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.LoanProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * A product as it appears in a list.
 *
 * <p>Carries the version currently on sale, in full, because that is the only
 * thing anybody wants to know about a product they have not opened yet. Null
 * when none is, which is how a catalogue shows a product that has been drafted
 * but not launched.
 */
@Schema(description = "A product, with whichever version is currently on sale")
public record ProductSummaryResponse(
        @Schema(example = "ELOAN") String code,
        @Schema(example = "e-Loan") String name,
        @Schema(description = "The name in Bangla, where one is configured") String nameBn,
        @Schema(example = "TERM_LOAN") String productType,
        @Schema(example = "PERSONAL") String category,
        String description,
        @Schema(example = "ACTIVE") String status,
        @Schema(description = "How many versions have ever been issued", example = "1")
        int versionCount,
        ProductVersionResponse currentVersion) {

    public static ProductSummaryResponse from(LoanProduct product, LocalDate today) {
        return new ProductSummaryResponse(
                product.getCode(),
                product.getName(),
                product.getNameBn(),
                product.getProductType(),
                product.getCategory(),
                product.getDescription(),
                product.getStatus(),
                product.getVersions().size(),
                product.sellableVersionOn(today)
                        .map(ProductVersionResponse::full)
                        .orElse(null));
    }
}
