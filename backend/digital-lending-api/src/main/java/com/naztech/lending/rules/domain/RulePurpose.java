package com.naztech.lending.rules.domain;

/**
 * What a group of rules is for.
 *
 * <p>Eligibility decides whether the customer may apply at all. Credit and
 * screening arrive with the modules that use them; they are enumerated now
 * because the column already constrains them and a group must declare one.
 */
public enum RulePurpose {
    ELIGIBILITY,
    CREDIT,
    SCREENING
}
