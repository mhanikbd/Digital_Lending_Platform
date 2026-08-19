package com.naztech.lending.customer.service;

import com.naztech.lending.auth.domain.RoleScope;
import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.customer.domain.Customer;
import com.naztech.lending.customer.dto.CustomerDetailResponse;
import com.naztech.lending.customer.dto.CustomerSummaryResponse;
import com.naztech.lending.customer.repository.CustomerRepository;
import com.naztech.lending.organization.service.OrganizationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the customer master, through the reader's organisational scope.
 *
 * <p>The filter is applied here rather than left to callers. A repository that
 * can be asked for every customer is a repository somebody will ask, and the
 * branch a customer belongs to is not a detail a screen should be trusted to
 * remember.
 *
 * <p>Reads only. Creating and amending customers is the account-opening journey
 * in Milestone 12, which needs the KYC verification from Milestone 10 to mean
 * anything first.
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final OrganizationService organization;

    public CustomerService(CustomerRepository customers, OrganizationService organization) {
        this.customers = customers;
        this.organization = organization;
    }

    /** Every customer this person is entitled to see, and no others. */
    @Transactional(readOnly = true)
    public List<CustomerSummaryResponse> list(UUID userId) {
        return visibleTo(userId).stream().map(CustomerSummaryResponse::from).toList();
    }

    /**
     * One customer in full.
     *
     * <p>A customer outside the reader's scope answers exactly as one that does
     * not exist. Distinguishing them would turn this endpoint into a way of
     * discovering which branch holds a given customer id.
     */
    @Transactional(readOnly = true)
    public CustomerDetailResponse detail(UUID userId, String customerId) {
        Customer customer = customers.findWithAddressesByCustomerId(customerId)
                .filter(candidate -> isVisibleTo(userId, candidate))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No such customer"));

        // Touched inside the transaction so the collection is loaded before the
        // response is assembled outside it.
        customer.getIdentifications().size();

        return CustomerDetailResponse.from(customer, LocalDate.now());
    }

    private List<Customer> visibleTo(UUID userId) {
        if (organization.widestScopeOf(userId) == RoleScope.HEAD_OFFICE) {
            // Not filtered at all, which is not the same as filtered to every
            // branch: it also takes in customers not yet attached to one.
            return customers.findAllByOrderByCustomerIdAsc();
        }
        Set<UUID> branches = organization.visibleUnitIds(userId);
        if (branches.isEmpty()) {
            return List.of();
        }
        return customers.findByHomeBranchIdInOrderByCustomerIdAsc(branches);
    }

    private boolean isVisibleTo(UUID userId, Customer customer) {
        if (organization.widestScopeOf(userId) == RoleScope.HEAD_OFFICE) {
            return true;
        }
        if (customer.getHomeBranch() == null) {
            // Held by head office and attached nowhere, so nobody scoped to a
            // branch has a claim on it.
            return false;
        }
        return organization.visibleUnitIds(userId).contains(customer.getHomeBranch().getId());
    }
}
