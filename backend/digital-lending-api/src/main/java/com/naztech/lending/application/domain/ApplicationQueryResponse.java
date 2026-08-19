package com.naztech.lending.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One answer to a credit query.
 *
 * <p>Append only. A query answered twice keeps both, because the first answer is
 * usually what explains why there was a second.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_query_response")
public class ApplicationQueryResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "query_id", nullable = false, updatable = false)
    private ApplicationQuery query;

    @Column(nullable = false, length = 2000, updatable = false)
    private String response;

    /** An answer may be a document rather than a sentence. */
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "responded_by_user_id", updatable = false)
    private UUID respondedByUserId;

    @Column(name = "responded_by", nullable = false, length = 64, updatable = false)
    private String respondedBy;

    @Column(name = "responded_by_role", length = 40, updatable = false)
    private String respondedByRole;

    @Column(name = "responded_at", nullable = false, updatable = false)
    private Instant respondedAt = Instant.now();

    protected ApplicationQueryResponse() {
        // for JPA
    }

    public ApplicationQueryResponse(String response, UUID respondedByUserId, String respondedBy,
                                    String respondedByRole, Instant respondedAt) {
        this.response = response;
        this.respondedByUserId = respondedByUserId;
        this.respondedBy = respondedBy;
        this.respondedByRole = respondedByRole;
        this.respondedAt = respondedAt;
    }

    void attachTo(ApplicationQuery owner) {
        this.query = owner;
    }

    public void withDocument(UUID documentId) {
        this.documentId = documentId;
    }

    public Long getId() {
        return id;
    }

    public String getResponse() {
        return response;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getRespondedByUserId() {
        return respondedByUserId;
    }

    public String getRespondedBy() {
        return respondedBy;
    }

    public String getRespondedByRole() {
        return respondedByRole;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
