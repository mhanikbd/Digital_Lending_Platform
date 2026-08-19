package com.naztech.lending.auth.domain;

/**
 * Why an authentication attempt ended as it did. Recorded for every attempt,
 * including ones where no identity matched, because that is the case an
 * investigator cares about most.
 *
 * <p>The outcome is written to the audit trail in full. What is returned to the
 * caller is deliberately vaguer: see AuthenticationService.
 */
public enum LoginOutcome {

    SUCCESS,
    UNKNOWN_USER,
    BAD_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_NOT_ACTIVE,
    MFA_REQUIRED,
    MFA_FAILED,
    DEVICE_NOT_TRUSTED
}
