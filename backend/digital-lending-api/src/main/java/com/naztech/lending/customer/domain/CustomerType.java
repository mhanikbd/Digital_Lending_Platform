package com.naztech.lending.customer.domain;

/** The kinds of customer the specification requires the platform to hold. */
public enum CustomerType {

    INDIVIDUAL,

    /** Two or more people on one relationship. */
    JOINT,

    /** Under eighteen, and therefore always operated through a guardian. */
    MINOR,

    /** Operates a minor's relationship. */
    GUARDIAN,

    /** Acts on a relationship they do not own, under a mandate. */
    AUTHORIZED_PERSON,

    /** A company. */
    BUSINESS,

    /** A business that is legally its owner. */
    SOLE_PROPRIETOR;

    /** True when the relationship is a person rather than an entity. */
    public boolean isNaturalPerson() {
        return this != BUSINESS;
    }

    /** True when the relationship cannot be operated by its own holder. */
    public boolean requiresGuardian() {
        return this == MINOR;
    }
}
