package com.naztech.lending.customer.domain;

/** Whether the relationship is live. Closed customers are kept, never deleted. */
public enum CustomerStatus {
    ACTIVE,
    DORMANT,
    CLOSED;

    public boolean canTransact() {
        return this == ACTIVE;
    }
}
