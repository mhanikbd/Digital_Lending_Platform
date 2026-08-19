package com.naztech.lending.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One thing that can be permitted.
 *
 * <p>The code is the contract. It appears in {@code @PreAuthorize} expressions
 * and inside issued access tokens, so renaming one is an API change and not a
 * refactor.
 */
@Entity
@Table(schema = "auth", name = "t_permission")
public class Permission {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 80, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 40)
    private String module;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Permission() {
        // for JPA
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

    public String getModule() {
        return module;
    }
}
