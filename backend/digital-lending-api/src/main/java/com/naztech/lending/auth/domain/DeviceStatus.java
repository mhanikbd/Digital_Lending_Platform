package com.naztech.lending.auth.domain;

/** Registration state of a handset bound to an identity. */
public enum DeviceStatus {

    /** Registered but not yet proven by OTP. Cannot be used to sign in. */
    PENDING,

    /** Proven by OTP and bound. A customer PIN is only accepted from one of these. */
    TRUSTED,

    /** Barred, whether reported lost or blocked by the bank. */
    BLOCKED
}
