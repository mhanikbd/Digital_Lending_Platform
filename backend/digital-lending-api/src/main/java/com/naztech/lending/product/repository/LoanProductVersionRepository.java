package com.naztech.lending.product.repository;

import com.naztech.lending.product.domain.LoanProductVersion;
import com.naztech.lending.product.domain.VersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanProductVersionRepository extends JpaRepository<LoanProductVersion, UUID> {

    /**
     * One version with everything a calculation needs.
     *
     * <p>Fees, tenures and risk limits are three separate collections. Fetching
     * them in one graph would multiply the rows together - three fees times four
     * tenures times three limits is thirty-six rows to assemble ten objects
     * from - so they are loaded as separate selects and the entity graph names
     * only the product.
     */
    @EntityGraph(attributePaths = {"product"})
    Optional<LoanProductVersion> findWithProductById(UUID id);

    List<LoanProductVersion> findByProductIdOrderByVersionNoAsc(UUID productId);

    Optional<LoanProductVersion> findByProductIdAndVersionNo(UUID productId, int versionNo);

    Optional<LoanProductVersion> findByProductIdAndStatus(UUID productId, VersionStatus status);

    /**
     * The live version of a product named by its code.
     *
     * <p>The partial unique index in the schema guarantees this is at most one
     * row, which is why it returns an Optional rather than a list.
     */
    @Query("""
            SELECT v FROM LoanProductVersion v
            JOIN FETCH v.product p
            WHERE p.code = :code AND v.status = com.naztech.lending.product.domain.VersionStatus.ACTIVE
            """)
    Optional<LoanProductVersion> findActiveByProductCode(@Param("code") String code);

    /** The highest version number issued for a product, absent when none is. */
    @Query("SELECT MAX(v.versionNo) FROM LoanProductVersion v WHERE v.product.id = :productId")
    Optional<Integer> findHighestVersionNo(@Param("productId") UUID productId);
}
