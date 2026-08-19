package com.naztech.lending.product.repository;

import com.naztech.lending.product.domain.LoanProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {

    /**
     * The catalogue, with every version attached.
     *
     * <p>Versions come with the row because the only interesting question about
     * a product is what it currently costs, and answering it per product would
     * be a query per product.
     */
    @EntityGraph(attributePaths = {"versions"})
    List<LoanProduct> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {"versions"})
    Optional<LoanProduct> findWithVersionsByCode(String code);

    Optional<LoanProduct> findByCode(String code);

    boolean existsByCode(String code);
}
