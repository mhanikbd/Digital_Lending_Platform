package com.naztech.lending.product.dto;

import com.naztech.lending.product.domain.LoanProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * A product and every version it has ever had.
 *
 * <p>Retired versions are included deliberately. Loans are still being repaid
 * under terms that are no longer sold, and a banker asked about one of them must
 * be able to find what those terms were.
 */
@Schema(description = "A product with its full version history")
public record ProductDetailResponse(
        @Schema(example = "ELOAN") String code,
        @Schema(example = "e-Loan") String name,
        String nameBn,
        @Schema(example = "TERM_LOAN") String productType,
        @Schema(example = "PERSONAL") String category,
        String description,
        @Schema(example = "ACTIVE") String status,
        @Schema(description = "The version a new application would be judged under")
        ProductVersionResponse currentVersion,
        @Schema(description = "Every version, newest first") List<ProductVersionResponse> versions) {

    public static ProductDetailResponse from(LoanProduct product, LocalDate today) {
        return new ProductDetailResponse(
                product.getCode(),
                product.getName(),
                product.getNameBn(),
                product.getProductType(),
                product.getCategory(),
                product.getDescription(),
                product.getStatus(),
                product.sellableVersionOn(today).map(ProductVersionResponse::full).orElse(null),
                product.getVersions().stream()
                        .sorted(Comparator.comparingInt(
                                com.naztech.lending.product.domain.LoanProductVersion::getVersionNo)
                                .reversed())
                        .map(ProductVersionResponse::full)
                        .toList());
    }
}
