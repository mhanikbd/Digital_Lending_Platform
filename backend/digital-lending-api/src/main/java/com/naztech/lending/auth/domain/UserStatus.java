package com.naztech.lending.auth.domain;

/** Lifecycle of an identity. Only ACTIVE may authenticate. */
public enum UserStatus {

    ACTIVE,

    /** Temporarily barred by failed attempts; clears when locked_until passes. */
    LOCKED,

    /** Barred by a person, and stays barred until a person clears it. */
    SUSPENDED,

    /** Closed. Kept for the audit trail rather than deleted. */
    DISABLED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
