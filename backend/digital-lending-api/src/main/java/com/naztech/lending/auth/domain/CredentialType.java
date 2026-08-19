package com.naztech.lending.auth.domain;

/** What kind of secret an identity presents. One identity may hold both. */
public enum CredentialType {

    /** Staff password. */
    PASSWORD,

    /** Customer 6 digit PIN. */
    PIN
}
