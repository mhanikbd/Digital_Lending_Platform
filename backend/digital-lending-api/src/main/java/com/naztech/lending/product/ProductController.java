package com.naztech.lending.product;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.product.dto.NewProductRequest;
import com.naztech.lending.product.dto.ProductDetailResponse;
import com.naztech.lending.product.dto.ProductSummaryResponse;
import com.naztech.lending.product.dto.ProductVersionResponse;
import com.naztech.lending.product.dto.VersionAmendmentRequest;
import com.naztech.lending.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The product catalogue and its versions.
 *
 * <p>Reading is open to every staff role: an officer who cannot see what the
 * bank sells cannot sell it. Changing the terms is not, and it is a different
 * permission rather than a different role, so a bank that wants a product
 * committee instead of an administrator grants a row rather than asking for a
 * release.
 *
 * <p>There is no endpoint that edits a live version. That is the whole point of
 * Milestone 14: repricing drafts a new version and activates it.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "The loan product catalogue and its versions")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product.view')")
    @Operation(summary = "The catalogue",
            description = "Every product, each showing the version currently on sale. A product "
                    + "with no live version returns null for it, which is how a drafted but "
                    + "unlaunched product appears.")
    public ApiResponse<List<ProductSummaryResponse>> list() {
        return ApiResponse.success(products.list());
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('product.view')")
    @Operation(summary = "One product in full",
            description = "Includes every version ever issued, retired ones included: loans are "
                    + "still being repaid under terms that are no longer sold, and the retired "
                    + "version is the only record of what they were.")
    public ApiResponse<ProductDetailResponse> detail(@PathVariable String code) {
        return ApiResponse.success(products.detail(code));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product.configure')")
    @Operation(summary = "Register a product",
            description = "Creates the product and the first draft of its terms in one call, "
                    + "because a product with no version cannot be sold and there is no reason to "
                    + "create one in that state. The version still has to be activated.")
    public ApiResponse<ProductVersionResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody NewProductRequest request) {
        return ApiResponse.success(products.create(request, authorOf(jwt)));
    }

    @PostMapping("/{code}/versions")
    @PreAuthorize("hasAuthority('product.configure')")
    @Operation(summary = "Draft the next version",
            description = "Copies the live version - or the newest one if none is live - applies "
                    + "the amendments in the body, and saves the result as a draft. The draft is "
                    + "not on sale until it is activated. Only one draft may exist at a time.")
    public ApiResponse<ProductVersionResponse> draft(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable String code,
                                                     @Valid @RequestBody(required = false)
                                                     VersionAmendmentRequest amendment) {
        return ApiResponse.success(products.draftNextVersion(code,
                amendment == null ? VersionAmendmentRequest.none() : amendment, authorOf(jwt)));
    }

    @PutMapping("/{code}/versions/{versionNo}")
    @PreAuthorize("hasAuthority('product.configure')")
    @Operation(summary = "Amend a draft",
            description = "Changes the fields named in the body. A version that is active or "
                    + "retired is refused: its terms are what somebody's loan was written on.")
    public ApiResponse<ProductVersionResponse> amend(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable String code,
                                                     @PathVariable int versionNo,
                                                     @Valid @RequestBody
                                                     VersionAmendmentRequest amendment) {
        return ApiResponse.success(
                products.amendDraft(code, versionNo, amendment, authorOf(jwt)));
    }

    @PostMapping("/{code}/versions/{versionNo}/activate")
    @PreAuthorize("hasAuthority('product.configure')")
    @Operation(summary = "Put a draft on sale",
            description = "Retires whichever version is currently live and activates this one, in "
                    + "a single transaction, so the product is never briefly unsellable.")
    public ApiResponse<ProductVersionResponse> activate(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable String code,
                                                        @PathVariable int versionNo) {
        return ApiResponse.success(products.activate(code, versionNo, authorOf(jwt)));
    }

    @PostMapping("/{code}/versions/{versionNo}/retire")
    @PreAuthorize("hasAuthority('product.configure')")
    @Operation(summary = "Withdraw a version",
            description = "Takes it off sale without replacing it, which leaves the product with "
                    + "nothing live. Eligibility and quotation then answer that it is not "
                    + "currently on sale rather than falling back to older terms.")
    public ApiResponse<ProductVersionResponse> retire(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable String code,
                                                      @PathVariable int versionNo) {
        return ApiResponse.success(products.retire(code, versionNo, authorOf(jwt)));
    }

    /**
     * Who made the change.
     *
     * <p>The username - an employee number for bank staff - rather than the
     * subject id, because the audit column is read by people and a UUID makes
     * them go and look it up. Falls back to the subject when the claim is
     * absent, so the column is never left guessing.
     */
    private String authorOf(Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        return username != null ? username : jwt.getSubject();
    }
}
