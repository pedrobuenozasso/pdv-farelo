package com.farelo.api.fiscal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/** Durable guard around one authorization transport attempt for one document. */
@Entity
@Table(name = "fiscal_submission_attempt")
public class FiscalSubmissionAttempt {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private FiscalDocument document;

    @Column(name = "access_key", nullable = false, length = 44, updatable = false)
    private String accessKey;

    @Column(name = "request_sha256", nullable = false, length = 64, updatable = false)
    private String requestSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FiscalSubmissionAttemptStatus status = FiscalSubmissionAttemptStatus.RESERVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FiscalSubmissionAttempt() { }

    public FiscalSubmissionAttempt(FiscalDocument document, String accessKey, String requestSha256) {
        this.document = Objects.requireNonNull(document, "document");
        if (accessKey == null || !accessKey.matches("[0-9]{44}")
                || requestSha256 == null || !requestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid fiscal submission identity");
        }
        this.accessKey = accessKey;
        this.requestSha256 = requestSha256;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public FiscalDocument getDocument() { return document; }
    public String getAccessKey() { return accessKey; }
    public String getRequestSha256() { return requestSha256; }
    public FiscalSubmissionAttemptStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    void dispatch() { transition(FiscalSubmissionAttemptStatus.RESERVED, FiscalSubmissionAttemptStatus.DISPATCHING); }
    void uncertain() { transition(FiscalSubmissionAttemptStatus.DISPATCHING, FiscalSubmissionAttemptStatus.UNCERTAIN); }

    void resolve() {
        if (status != FiscalSubmissionAttemptStatus.DISPATCHING
                && status != FiscalSubmissionAttemptStatus.UNCERTAIN) {
            throw new IllegalStateException("submission attempt cannot be resolved");
        }
        status = FiscalSubmissionAttemptStatus.RESOLVED;
    }

    private void transition(FiscalSubmissionAttemptStatus from, FiscalSubmissionAttemptStatus to) {
        if (status != from) throw new IllegalStateException("submission attempt cannot transition");
        status = to;
    }
}
