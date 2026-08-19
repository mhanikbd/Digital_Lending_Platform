package com.naztech.lending.application.domain;

/**
 * Which applicant a row describes.
 *
 * <p>Only PRIMARY is used today. Joint applicants and guarantors get their own
 * rows when the products that need them arrive - which is why the applicant is
 * a table rather than a set of columns on the application.
 */
public enum ApplicantType {
    PRIMARY,
    JOINT,
    GUARANTOR
}
