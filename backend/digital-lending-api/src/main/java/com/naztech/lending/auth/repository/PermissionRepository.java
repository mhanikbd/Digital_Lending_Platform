package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.Permission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();
}
