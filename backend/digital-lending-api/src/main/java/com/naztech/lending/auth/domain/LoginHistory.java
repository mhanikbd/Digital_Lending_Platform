package com.naztech.lending.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One authentication attempt, successful or not.
 *
 * <p>Append only. Rows are written for attempts where no identity matched too,
 * because a run of those against different usernames is exactly the pattern an
 * investigator is looking for. The secret that was presented is never recorded.
 */
@Entity
@Table(schema = "auth", name = "t_login_history")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(name = "username_attempted", nullable = false, length = 64, updatable = false)
    private String usernameAttempted;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, updatable = false)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private LoginOutcome outcome;

    @Column(length = 120, updatable = false)
    private String reason;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "device_id", length = 128, updatable = false)
    private String deviceId;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected LoginHistory() {
        // for JPA
    }

    public LoginHistory(UserAccount user, String usernameAttempted, UserType userType, LoginOutcome outcome) {
        this.user = user;
        this.usernameAttempted = usernameAttempted;
        this.userType = userType;
        this.outcome = outcome;
    }

    public LoginHistory withReason(String reason) {
        this.reason = truncate(reason, 120);
        return this;
    }

    public LoginHistory withCaller(String ipAddress, String userAgent, String deviceId) {
        this.ipAddress = ipAddress;
        this.userAgent = truncate(userAgent, 255);
        this.deviceId = truncate(deviceId, 128);
        return this;
    }

    public LoginHistory withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public String getUsernameAttempted() {
        return usernameAttempted;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
