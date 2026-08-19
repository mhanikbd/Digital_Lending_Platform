package com.naztech.lending.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A refresh-token session.
 *
 * <p>The access token is stateless and short lived; this is the part that is
 * revocable. Only the SHA-256 of the refresh token is stored, so reading this
 * table does not let anyone replay a session.
 */
@Entity
@Table(schema = "auth", name = "t_session")
public class UserSession {

    // Assigned on construction, not by the database: see UserAccount.
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private UserDevice device;

    @Column(name = "refresh_token_hash", nullable = false, length = 64, updatable = false)
    private String refreshTokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected UserSession() {
        // for JPA
    }

    public UserSession(UserAccount user, UserDevice device, String refreshTokenHash, Instant expiresAt) {
        this.user = user;
        this.device = device;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
    }

    /** Usable only while neither revoked nor expired. */
    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(String reason, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
        }
    }

    public void markUsed(Instant now) {
        lastUsedAt = now;
    }

    public void describeCaller(String ipAddress, String userAgent) {
        this.ipAddress = ipAddress;
        this.userAgent = truncate(userAgent, 255);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public UserDevice getDevice() {
        return device;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
