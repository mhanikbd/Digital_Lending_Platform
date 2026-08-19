package com.naztech.lending.application.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A question the credit analyst put to the branch.
 *
 * <p>§23: "Queries must retain original questions and responses in the audit
 * trail." So the question is written once and never edited, and each answer is a
 * row beneath it. Editing a question after it has been answered would leave an
 * answer to something nobody asked.
 *
 * <p>A query answered twice keeps both answers. The second is usually the one
 * that mattered, and the first is usually the one that explains why there was a
 * second.
 */
@Entity
@Table(schema = "application", name = "t_loan_application_query")
public class ApplicationQuery {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    /** Numbered within the application, so people can refer to "query 2". */
    @Column(name = "query_no", nullable = false, updatable = false)
    private short queryNo;

    @Column(nullable = false, length = 2000, updatable = false)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "query_type", nullable = false, length = 30, updatable = false)
    private QueryType queryType = QueryType.INFORMATION;

    @Column(name = "raised_by_user_id", updatable = false)
    private UUID raisedByUserId;

    @Column(name = "raised_by", nullable = false, length = 64, updatable = false)
    private String raisedBy;

    @Column(name = "raised_by_role", length = 40, updatable = false)
    private String raisedByRole;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueryStatus status = QueryStatus.OPEN;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "query", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("respondedAt ASC")
    private List<ApplicationQueryResponse> responses = new ArrayList<>();

    protected ApplicationQuery() {
        // for JPA
    }

    public ApplicationQuery(UUID applicationId, int queryNo, String question, QueryType type,
                            UUID raisedByUserId, String raisedBy, String raisedByRole,
                            Instant raisedAt) {
        this.applicationId = applicationId;
        this.queryNo = (short) queryNo;
        this.question = question;
        this.queryType = type;
        this.raisedByUserId = raisedByUserId;
        this.raisedBy = raisedBy;
        this.raisedByRole = raisedByRole;
        this.raisedAt = raisedAt;
    }

    /** Records an answer. The query becomes answered, not closed: closing is a decision. */
    public void answer(ApplicationQueryResponse response) {
        response.attachTo(this);
        responses.add(response);
        this.status = QueryStatus.ANSWERED;
    }

    /** The analyst is satisfied. Kept separate from answering, which the branch does. */
    public void close(Instant at) {
        this.status = QueryStatus.CLOSED;
        this.closedAt = at;
    }

    public boolean isOpen() {
        return status == QueryStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public short getQueryNo() {
        return queryNo;
    }

    public String getQuestion() {
        return question;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public UUID getRaisedByUserId() {
        return raisedByUserId;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public String getRaisedByRole() {
        return raisedByRole;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public QueryStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<ApplicationQueryResponse> getResponses() {
        return responses;
    }
}
