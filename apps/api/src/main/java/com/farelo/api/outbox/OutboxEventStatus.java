package com.farelo.api.outbox;

/**
 * Lifecycle of an {@link OutboxEvent} row. Mirrors the {@code VARCHAR} +
 * {@code CHECK} constraint in {@code V10__create_outbox_event_table.sql}
 * one for one — extending this enum requires a follow-up migration to
 * extend that constraint (same trade-off already accepted for {@code
 * com.farelo.api.command.CommandStatus}/{@code
 * com.farelo.api.ordering.OrderStatus}).
 */
public enum OutboxEventStatus {

    /** Written by {@link OutboxPublisher}, not yet handled by {@link OutboxWorker}. */
    PENDING,

    /** Picked up and handled (today: only logged — see {@link OutboxWorker}). */
    PROCESSED

}
