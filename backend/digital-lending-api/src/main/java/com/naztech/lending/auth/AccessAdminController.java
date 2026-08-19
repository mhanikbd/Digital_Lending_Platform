package com.naztech.lending.auth;

import com.naztech.lending.auth.domain.UserType;
import com.naztech.lending.auth.dto.BankUserResponse;
import com.naztech.lending.auth.dto.PermissionResponse;
import com.naztech.lending.auth.dto.RoleResponse;
import com.naztech.lending.auth.repository.PermissionRepository;
import com.naztech.lending.auth.repository.RoleRepository;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads over the access-control configuration: who exists, what roles the bank
 * has defined, and what each role permits.
 *
 * <p>Every method is guarded by a permission rather than by a role name. That
 * is the whole point of Milestone 6: a bank can decide that its Unit Heads may
 * read the role catalogue by inserting one row, with no change here. Writing
 * {@code hasRole("ADMIN")} would put that decision back into the code.
 *
 * <p>Reads only. Creating users and re-permissioning roles belongs with the
 * administration screens in Milestone 35, and the audit trail those changes
 * require does not exist yet.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Access administration", description = "Users, roles and the permission catalogue")
public class AccessAdminController {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserAccountRepository users;

    public AccessAdminController(RoleRepository roles,
                                 PermissionRepository permissions,
                                 UserAccountRepository users) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin.role.view')")
    @Operation(summary = "List roles and what they permit",
            description = "Requires the admin.role.view permission.")
    @Transactional(readOnly = true)
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.success(
                roles.findAllByOrderByCodeAsc().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('admin.role.view')")
    @Operation(summary = "List the permission catalogue",
            description = "Every permission the platform knows about, grouped by module. "
                    + "Requires the admin.role.view permission.")
    @Transactional(readOnly = true)
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.success(
                permissions.findAllByOrderByModuleAscCodeAsc().stream()
                        .map(PermissionResponse::from).toList());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.view')")
    @Operation(summary = "List bank users",
            description = "Staff accounts and the roles they hold. Never returns a credential. "
                    + "Requires the admin.user.view permission.")
    @Transactional(readOnly = true)
    public ApiResponse<List<BankUserResponse>> listBankUsers() {
        return ApiResponse.success(
                users.findByUserTypeOrderByUsernameAsc(UserType.BANK_USER).stream()
                        .map(BankUserResponse::from).toList());
    }
}
