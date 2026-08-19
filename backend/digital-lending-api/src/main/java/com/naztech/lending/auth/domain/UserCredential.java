package com.naztech.lending.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A hashed secret belonging to an identity. Only ever holds the hash, and there
 * is deliberately no accessor that could be mistaken for the plaintext.
 */
@Entity
@Table(schema = "auth", name = "t_user_credential")
public class UserCredential {

    // Assigned on construction, not by the database: see UserAccount.
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 20, updatable = false)
    private CredentialType credentialType;

    @Column(name = "secret_hash", nullable = false, length = 120)
    private String secretHash;

    @Column(nullable = false, length = 20)
    private String algorithm = "BCRYPT";

    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected UserCredential() {
        // for JPA
    }

    public UserCredential(UserAccount user, CredentialType credentialType, String secretHash) {
        this.user = user;
        this.credentialType = credentialType;
        this.secretHash = secretHash;
    }

    /** Replaces the secret. Callers pass an already hashed value. */
    public void rotate(String newSecretHash, Instant now) {
        this.secretHash = newSecretHash;
        this.rotatedAt = now;
        this.updatedAt = now;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public String getSecretHash() {
        return secretHash;
    }
}
