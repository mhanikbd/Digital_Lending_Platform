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
 * A handset bound to one identity.
 *
 * <p>Device binding is what makes a 6 digit PIN defensible: the PIN alone is
 * weak, but a PIN that is only accepted from a handset already proven by OTP is
 * two factors. A device therefore starts PENDING and only becomes TRUSTED once
 * an OTP has been verified from it.
 */
@Entity
@Table(schema = "auth", name = "t_device")
public class UserDevice {

    // Assigned on construction, not by the database: see UserAccount.
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @Column(name = "device_id", nullable = false, length = 128, updatable = false)
    private String deviceId;

    @Column(length = 20)
    private String platform;

    @Column(length = 80)
    private String model;

    @Column(name = "os_version", length = 40)
    private String osVersion;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.PENDING;

    @Column(name = "biometric_enabled", nullable = false)
    private boolean biometricEnabled;

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected UserDevice() {
        // for JPA
    }

    public UserDevice(UserAccount user, String deviceId) {
        this.user = user;
        this.deviceId = deviceId;
    }

    public boolean isTrusted() {
        return status == DeviceStatus.TRUSTED;
    }

    /** Promotes the handset once an OTP has been verified from it. */
    public void markTrusted(Instant now) {
        if (status == DeviceStatus.BLOCKED) {
            throw new IllegalStateException("A blocked device cannot be trusted again without review");
        }
        status = DeviceStatus.TRUSTED;
        boundAt = now;
        lastSeenAt = now;
        updatedAt = now;
    }

    public void block(Instant now) {
        status = DeviceStatus.BLOCKED;
        updatedAt = now;
    }

    public void touch(Instant now) {
        lastSeenAt = now;
        updatedAt = now;
    }

    public void describe(String platform, String model, String osVersion, String appVersion) {
        this.platform = platform;
        this.model = model;
        this.osVersion = osVersion;
        this.appVersion = appVersion;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public Instant getBoundAt() {
        return boundAt;
    }

    public boolean isBiometricEnabled() {
        return biometricEnabled;
    }

    public void setBiometricEnabled(boolean biometricEnabled) {
        this.biometricEnabled = biometricEnabled;
    }
}
