package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A remark somebody made about the file.
 *
 * <p>The state the file was in is recorded with it. Without that, a note reads
 * as if it were made about the application as it is now rather than as it was
 * when somebody looked at it.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_comment")
public class ApplicationComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "state_code", length = 40, updatable = false)
    private String stateCode;

    @Column(name = "author_user_id", updatable = false)
    private UUID authorUserId;

    @Column(name = "author_username", nullable = false, length = 64, updatable = false)
    private String authorUsername;

    @Column(name = "author_role", length = 40, updatable = false)
    private String authorRole;

    @Column(nullable = false, length = 2000, updatable = false)
    private String comment;

    /**
     * An internal note is not shown to the customer. A bank needs somewhere to
     * write "third application this quarter" that is not a letter.
     */
    @Column(name = "internal_only", nullable = false, updatable = false)
    private boolean internalOnly = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ApplicationComment() {
        // for JPA
    }

    public ApplicationComment(UUID applicationId, String stateCode, UUID authorUserId,
                              String authorUsername, String authorRole, String comment,
                              boolean internalOnly, Instant createdAt) {
        this.applicationId = applicationId;
        this.stateCode = stateCode;
        this.authorUserId = authorUserId;
        this.authorUsername = authorUsername;
        this.authorRole = authorRole;
        this.comment = comment;
        this.internalOnly = internalOnly;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getStateCode() {
        return stateCode;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getAuthorRole() {
        return authorRole;
    }

    public String getComment() {
        return comment;
    }

    public boolean isInternalOnly() {
        return internalOnly;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
