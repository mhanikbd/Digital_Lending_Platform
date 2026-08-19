package com.naztech.lending.customer.domain;

/**
 * The bank's standing view of a customer.
 *
 * <p>Deliberately coarse. Fine-grained scoring belongs to the scorecard in
 * Milestone 18 and is recomputed per application; this is the durable label a
 * relationship carries between them.
 */
public enum RiskProfile {
    LOW,
    MEDIUM,
    HIGH
}
