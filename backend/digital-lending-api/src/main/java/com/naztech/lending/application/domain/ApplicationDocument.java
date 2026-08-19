package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A document attached to the file.
 *
 * <p>Object storage holds the bytes; this row holds the fact that they belong to
 * this application. The document module of Milestone 9 will own the object
 * itself, and {@code storageKey} is what it will point at - which is why the key
 * is a string here rather than a foreign key to a table that does not exist yet.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_document")
public class ApplicationDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private LoanApplication application;

    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "uploaded_by", nullable = false, length = 64, updatable = false)
    private String uploadedBy = "system";

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    protected ApplicationDocument() {
        // for JPA
    }

    public ApplicationDocument(String documentType, String fileName, String storageKey,
                               String uploadedBy) {
        this.documentType = documentType;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
    }

    void attachTo(LoanApplication owner) {
        this.application = owner;
    }

    public void describeFile(String contentType, Long sizeBytes) {
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    /** Verified without a time is verified nobody can date, so both are set together. */
    public void markVerified(String by, Instant at) {
        this.verified = true;
        this.verifiedBy = by;
        this.verifiedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
