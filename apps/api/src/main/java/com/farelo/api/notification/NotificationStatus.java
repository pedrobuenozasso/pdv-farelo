package com.farelo.api.notification;

/**
 * Delivery lifecycle of a {@link Notification} — same three-state shape as
 * {@code com.farelo.api.printing.PrintJobStatus} ({@code PENDING}/success/
 * failure), the closest analog already in the codebase for "a durable record
 * of something that needs to be handed to an external system, whose outcome
 * is reported back later, asynchronously".
 *
 * <p><strong>No transition logic exists yet</strong> beyond the raw mutators
 * on {@link Notification} ({@link Notification#markSent()}/{@link
 * Notification#markFailed()}, themselves unvalidated — same "dumb mutator,
 * validation lives in the service" split {@code PrintJob} started with at
 * its own entity-only ticket, FARELO-071). Nothing calls them yet: which
 * component transitions a {@code Notification} out of {@code PENDING} (a
 * future WhatsApp adapter, FARELO-111/112) doesn't exist in this codebase
 * yet, so there is no real caller to validate transitions against — adding
 * that now would be guessing at a shape a future ticket hasn't decided.
 *
 * <p>Mirrors the {@code VARCHAR} + {@code CHECK} constraint in {@code
 * V22__create_notification_table.sql} one for one — extending this enum
 * requires a follow-up migration to extend that constraint (same trade-off
 * already accepted for {@code
 * com.farelo.api.printing.PrintJobStatus}/{@code
 * com.farelo.api.outbox.OutboxEventStatus}).
 */
public enum NotificationStatus {

    /** Written, not yet handed to a real channel adapter. */
    PENDING,

    /** Successfully delivered (e.g. accepted by the WhatsApp Cloud API). */
    SENT,

    /** Delivery was attempted and failed. */
    FAILED

}
