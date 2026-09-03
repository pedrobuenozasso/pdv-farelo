package com.farelo.api.notification;

/**
 * Why a {@link Notification} exists — mirrors the domain event names already
 * used elsewhere in the project (prompt mestre, seção 29: {@code
 * ORDER_CREATED}, {@code ORDER_READY}, {@code ORDER_CANCELLED}, {@code
 * COMMAND_CLOSED}, {@code PRINT_REQUESTED}, {@code PRINT_COMPLETED}, {@code
 * PRINT_FAILED}, {@code STOCK_LOW}, {@code STOCK_CRITICAL}, {@code
 * OUT_OF_STOCK}), restricted to the subset seção 19 actually names as
 * notification triggers: {@code ORDER_READY} ("Fluxo: ORDER_READY →
 * Notification Worker → WhatsApp") and the "notificações internas" trio —
 * "estoque baixo" ({@link #STOCK_LOW}), "estoque zerado" ({@link
 * #OUT_OF_STOCK}) and "falha de impressão" ({@link #PRINT_FAILED}). {@link
 * #STOCK_CRITICAL} is included alongside {@code STOCK_LOW}/{@code
 * OUT_OF_STOCK} for symmetry with the full estoque-mínimo event trio already
 * established in seção 17/29 (same three events {@code inventory} already
 * models) — seção 19 doesn't name it individually, but it is the same
 * category of inventory alert as the other two, and nothing suggests it was
 * deliberately excluded.
 *
 * <p>{@code ORDER_CREATED}/{@code ORDER_CANCELLED}/{@code COMMAND_CLOSED}/
 * {@code PRINT_REQUESTED}/{@code PRINT_COMPLETED} are deliberately
 * <strong>not</strong> included: they are real domain events (used by {@code
 * outbox}/{@code printing}), but seção 19 never names them as something that
 * triggers a WhatsApp/internal notification — adding them here would be
 * inventing a requirement, not modeling one already written down.
 *
 * <p><strong>No producer exists yet</strong> for any of these values —
 * that's FARELO-112 ("Disparar WhatsApp em ORDER_READY") and FARELO-113
 * ("Alertar estoque baixo"), both future tickets. This enum only makes the
 * type expressible; nothing in this ticket constructs a {@code Notification}
 * from a real trigger.
 *
 * <p>Mirrors the {@code VARCHAR} + {@code CHECK} constraint in {@code
 * V22__create_notification_table.sql} one for one — extending this enum
 * requires a follow-up migration to extend that constraint (same trade-off
 * already accepted for {@code
 * com.farelo.api.printing.PrintJobStatus}/{@code
 * com.farelo.api.outbox.OutboxEventStatus}).
 */
public enum NotificationType {
    ORDER_READY,
    STOCK_LOW,
    STOCK_CRITICAL,
    OUT_OF_STOCK,
    PRINT_FAILED
}
