package com.naztech.lending.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One grant: this role may take this action in this state.
 *
 * <p>Authoritative. The engine reads this and nothing else, which is what the
 * specification means by "do not write {@code if (role.equals("BM"))}".
 *
 * <p>The role is held as a code rather than as an association to the auth
 * module's entity. The database has the foreign key, so a typo is still refused;
 * the entity does not, because a module reaching into another module's tables
 * through a mapping is the same coupling as reaching into them through a query.
 */
@Entity
@Table(schema = "workflow", name = "t_role_state_map")
public class RoleStateMap {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "role_code", nullable = false, length = 40, updatable = false)
    private String roleCode;

    @Column(name = "state_code", nullable = false, length = 40, updatable = false)
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private WorkflowAction action;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RoleStateMap() {
        // for JPA
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public UUID getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getStateCode() {
        return stateCode;
    }

    public WorkflowAction getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }
}
