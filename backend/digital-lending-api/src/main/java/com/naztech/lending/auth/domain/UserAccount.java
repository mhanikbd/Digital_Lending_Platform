package com.naztech.lending.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An identity the platform can authenticate.
 *
 * <p>Lock-out state lives here rather than in Redis on purpose: a lock is a
 * security decision and must survive a cache flush. The counters only ever move
 * through the methods below, so the rule that decides when an account locks
 * exists in exactly one place.
 */
@Entity
@Table(schema = "auth", name = "t_user")
public class UserAccount {

    // Assigned on construction rather than by the database, so an instance has
    // an identity before it is ever saved. That keeps the id out of the set of
    // things that can be null, and lets this class be reasoned about - and
    // tested - without a persistence context. The cost is that Spring Data sees
    // a non-null id and issues merge rather than persist; at a few rows per
    // sign-in that is not a trade worth optimising.
    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20, updatable = false)
    private UserType userType;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "must_change_secret", nullable = false)
    private boolean mustChangeSecret;

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

    protected UserAccount() {
        // for JPA
    }

    public UserAccount(UserType userType, String username, String displayName) {
        this.userType = userType;
        this.username = username;
        this.displayName = displayName;
    }

    /**
     * True while a lock is still being served. A lock whose deadline has passed
     * is not a lock, so this is time-based rather than a stored flag.
     */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * True when this identity may attempt authentication at this instant.
     *
     * <p>A LOCKED status whose deadline has passed is spent and does not bar
     * anything - the lock is the deadline, not the label. Only the states a
     * person set deliberately, SUSPENDED and DISABLED, survive their own clock.
     */
    public boolean canAuthenticateAt(Instant now) {
        if (isLockedAt(now)) {
            return false;
        }
        return status == UserStatus.ACTIVE || status == UserStatus.LOCKED;
    }

    /**
     * Records a failed attempt, locking the account once the threshold is
     * reached. Returns true when this attempt is what caused the lock.
     */
    public boolean registerFailedAttempt(int threshold, Duration lockFor, Instant now) {
        failedAttempts = (short) (failedAttempts + 1);
        updatedAt = now;
        if (failedAttempts >= threshold) {
            lockedUntil = now.plus(lockFor);
            status = UserStatus.LOCKED;
            return true;
        }
        return false;
    }

    /** Clears failure state after a successful authentication. */
    public void registerSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        if (status == UserStatus.LOCKED) {
            status = UserStatus.ACTIVE;
        }
        lastLoginAt = now;
        updatedAt = now;
    }

    /** Releases a served lock so the next attempt is judged on its merits. */
    public void releaseExpiredLock(Instant now) {
        if (status == UserStatus.LOCKED && !isLockedAt(now)) {
            status = UserStatus.ACTIVE;
            failedAttempts = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UserType getUserType() {
        return userType;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public short getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isMustChangeSecret() {
        return mustChangeSecret;
    }

    public void setMustChangeSecret(boolean mustChangeSecret) {
        this.mustChangeSecret = mustChangeSecret;
    }
}
