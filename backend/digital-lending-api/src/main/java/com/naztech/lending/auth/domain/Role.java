package com.naztech.lending.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A job, and the set of things that job may do.
 *
 * <p>The link to permissions is data, not code. Changing what a Branch Manager
 * may do is an insert or a delete in t_role_permission, never a deployment,
 * which is the whole point of the platform rule against writing
 * {@code if (role.equals("BM"))}.
 */
@Entity
@Table(schema = "auth", name = "t_role")
public class Role {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false, length = 20)
    private RoleScope scopeLevel = RoleScope.BRANCH;

    /** Seeded with the product. A bank may re-permission it but not delete it. */
    @Column(name = "is_system", nullable = false)
    private boolean systemRole;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            schema = "auth",
            name = "t_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "system";

    @Version
    @Column(nullable = false)
    private long version;

    protected Role() {
        // for JPA
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RoleScope getScopeLevel() {
        return scopeLevel;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public String getStatus() {
        return status;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
