package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    /**
     * Permissions are fetched with the roles. Listing fourteen roles and then
     * lazily walking each one is fourteen extra queries for a screen that
     * always shows both.
     */
    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByCodeAsc();
}
