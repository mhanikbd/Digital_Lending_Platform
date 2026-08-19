package com.naztech.lending.auth.domain;

/** The three actors the platform authenticates. Mirrors ck_user_type in V2. */
public enum UserType {

    /** Head office and branch staff. Signs in with an employee id and password. */
    BANK_USER,

    /** Borrower. Signs in with a mobile number and a 6 digit PIN from a bound device. */
    CUSTOMER,

    /** Field officer originating applications on behalf of customers. */
    FIELD_OFFICER
}
