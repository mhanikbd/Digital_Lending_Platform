package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The applicant as they were on the day.
 *
 * <p>A copy of the customer record rather than a join to it. The customer moves
 * on - they change job, they move house - and a decision taken on last year's
 * facts has to keep showing last year's facts.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_applicant")
public class ApplicationApplicant {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private LoanApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_type", nullable = false, length = 20)
    private ApplicantType applicantType = ApplicantType.PRIMARY;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(length = 160)
    private String email;

    @Column(name = "national_id", length = 30)
    private String nationalId;

    @Column(length = 60)
    private String occupation;

    @Column(name = "employer_name", length = 160)
    private String employerName;

    @Column(length = 120)
    private String designation;

    @Column(name = "present_address", length = 400)
    private String presentAddress;

    @Column(name = "permanent_address", length = 400)
    private String permanentAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ApplicationApplicant() {
        // for JPA
    }

    public ApplicationApplicant(ApplicantType type, String fullName, String mobile) {
        this.applicantType = type;
        this.fullName = fullName;
        this.mobile = mobile;
    }

    void attachTo(LoanApplication owner) {
        this.application = owner;
    }

    public void describePerson(LocalDate dateOfBirth, String gender, String email,
                               String nationalId) {
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.email = email;
        this.nationalId = nationalId;
    }

    public void describeWork(String occupation, String employerName, String designation) {
        this.occupation = occupation;
        this.employerName = employerName;
        this.designation = designation;
    }

    public void describeWhereTheyLive(String present, String permanent) {
        this.presentAddress = present;
        this.permanentAddress = permanent;
    }

    public UUID getId() {
        return id;
    }

    public ApplicantType getApplicantType() {
        return applicantType;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getNationalId() {
        return nationalId;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getEmployerName() {
        return employerName;
    }

    public String getDesignation() {
        return designation;
    }

    public String getPresentAddress() {
        return presentAddress;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }
}
