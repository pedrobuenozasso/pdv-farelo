package com.farelo.api.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One row of the Transactional Outbox (FARELO-060, see {@code
 * com.farelo.api.outbox}'s package-info) — a durable record of a domain
 * event, written by {@link OutboxPublisher} in the same transaction as the
 * business write it represents, and later drained by {@link OutboxWorker}.
 *
 * <p>{@code aggregateType}/{@code aggregateId} name the domain entity the
 * event is about (e.g. {@code "Order"} / the order's id); {@code
 * eventType} names what happened (e.g. {@code "OrderCreated"}); {@code
 * payload} is that event's data, pre-serialized to a JSON string by {@link
 * OutboxPublisher} (Jackson) before this entity ever sees it — this entity
 * has no opinion on what shape the payload is, by design (see the
 * package-info's "dependency direction" note).
 *
 * <p><strong>{@code payload} storage</strong>: mapped as a plain {@code
 * String} with {@code @JdbcTypeCode(SqlTypes.JSON)} — Hibernate 6's native
 * JSON mapping (no extra library needed) writes the string as-is into the
 * {@code jsonb} column, since the Java side is already a String (no double
 * serialization).
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code
 * RANDOM} (UUIDv4) is used instead of UUIDv7.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    // EnumType.STRING, never ORDINAL — same reasoning as CommandStatus/
    // OrderStatus: storing the ordinal would silently corrupt data if this
    // enum's declaration order ever changes.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected OutboxEvent() {
        // required by JPA
    }

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Marks this event handled (today: by {@link OutboxWorker}'s stub —
     * see its javadoc for the future real-consumer extension point).
     */
    public void markProcessed() {
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutboxEvent other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
