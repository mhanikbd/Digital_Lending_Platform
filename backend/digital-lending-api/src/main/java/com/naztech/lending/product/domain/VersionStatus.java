package com.naztech.lending.product.domain;

/**
 * Where a version is in its life.
 *
 * <p>DRAFT may be edited. ACTIVE may not, because loans are being written
 * against it and changing the terms under a live application is the thing
 * versioning exists to prevent. RETIRED is kept forever: applications still
 * point at it, and a decision that cannot be reconstructed cannot be defended.
 */
public enum VersionStatus {
    DRAFT,
    ACTIVE,
    RETIRED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isSellable() {
        return this == ACTIVE;
    }
}
