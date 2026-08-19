package com.naztech.lending.application.domain;

/**
 * How an application reached the bank.
 *
 * <p>{@code FIELD_OFFICER} is the one with a rule attached: §22 requires every
 * such application to record which officer raised it, and the database enforces
 * that rather than trusting the caller.
 */
public enum SourceChannel {
    MOBILE_APP,
    WEBSITE,
    BRANCH,
    FIELD_OFFICER,
    CALL_CENTRE;

    /** True when the channel cannot be used without naming a field officer. */
    public boolean requiresFieldOfficer() {
        return this == FIELD_OFFICER;
    }
}
