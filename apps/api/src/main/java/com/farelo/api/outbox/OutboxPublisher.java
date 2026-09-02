package com.farelo.api.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes one {@link OutboxEvent} row per call, as part of the
 * Transactional Outbox pattern (FARELO-060 — see {@code
 * com.farelo.api.outbox}'s package-info).
 *
 * <p><strong>MUST be called from within the same transaction as the
 * business write it records.</strong> That's what makes the outbox
 * "transactional": either the domain write and this event both commit, or
 * neither does — if the caller's transaction rolls back, this row rolls
 * back with it. See {@code com.farelo.api.ordering.OrderService#create}
 * for the reference integration.
 *
 * <p>This class deliberately does <em>not</em> open its own transaction.
 * Instead, {@link #publish} uses {@link Propagation#MANDATORY}: calling it
 * with no transaction already active on the calling thread fails fast
 * with {@link IllegalTransactionStateException} rather than silently
 * persisting a row with no atomicity guarantee at all (plain {@code
 * JpaRepository#save} would otherwise happily open its own short-lived
 * transaction and commit immediately, defeating the entire point of this
 * class).
 */
@Service
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes {@code payload} to JSON (Jackson) and persists a {@code
     * PENDING} {@link OutboxEvent} row for it, within the caller's
     * existing transaction.
     *
     * @param aggregateType the domain entity type the event is about (e.g. {@code "Order"})
     * @param aggregateId   that entity's id
     * @param eventType     what happened (e.g. {@code "OrderCreated"})
     * @param payload       event data — any Jackson-serializable object (typically a record)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        outboxEventRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, serialize(payload, eventType)));
    }

    private String serialize(Object payload, String eventType) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Every payload used today is a simple record — reaching this
            // means Jackson genuinely can't serialize it (e.g. a cyclic
            // structure), an invariant violation rather than an expected
            // runtime condition, hence unchecked.
            throw new IllegalStateException("Failed to serialize outbox event payload for " + eventType, e);
        }
    }

}
