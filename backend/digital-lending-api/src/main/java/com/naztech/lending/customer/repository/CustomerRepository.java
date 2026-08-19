package com.naztech.lending.customer.repository;

import com.naztech.lending.customer.domain.Customer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByCustomerId(String customerId);

    /**
     * Everything, for a reader whose scope is not narrowed at all.
     *
     * <p>The branch is fetched with the row: a list of customers always shows
     * which branch holds each one, and a lazy walk would issue a query per
     * customer to say so.
     */
    @EntityGraph(attributePaths = {"homeBranch", "homeBranch.unitType"})
    List<Customer> findAllByOrderByCustomerIdAsc();

    /** The customers of the given branches, for a reader who is narrowed. */
    @EntityGraph(attributePaths = {"homeBranch", "homeBranch.unitType"})
    List<Customer> findByHomeBranchIdInOrderByCustomerIdAsc(Collection<UUID> branchIds);

    /**
     * One customer with everything hanging off them.
     *
     * <p>Addresses and documents are separate fetches rather than one graph:
     * joining two collections in a single query multiplies the rows together,
     * and three addresses with four documents becomes twelve rows to assemble
     * two lists from.
     */
    @EntityGraph(attributePaths = {"homeBranch", "homeBranch.unitType", "addresses"})
    Optional<Customer> findWithAddressesByCustomerId(String customerId);
}
