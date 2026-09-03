package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Payload for the {@code OrderReady} outbox event (FARELO-112), published by
 * {@link OrderService#markAsReady(UUID)} in the same transaction as the
 * {@code PREPARING} → {@code READY} status transition it describes. Lives in
 * {@code ordering}, not {@code com.farelo.api.outbox} — same "event payload
 * shapes belong to the domain that produces them" reasoning as {@link
 * OrderCreatedEvent} (see the outbox package's package-info, "dependency
 * direction").
 *
 * <p>Deliberately minimal — just {@code orderId} and {@code commandNumber}.
 * Its one real consumer today ({@code
 * com.farelo.api.notification.OrderReadyNotificationService}, dispatched by
 * {@code OutboxWorker}) only needs {@code orderId} to re-fetch the order from
 * the database (same "content comes from the database, not the event
 * payload" design already established by {@code
 * com.farelo.api.printing.PrintJobService} for {@code OrderCreated} — see its
 * javadoc) — {@code commandNumber} is included anyway for the same reason
 * {@link OrderCreatedEvent} carries one: a human inspecting {@code
 * outbox_event.payload} directly (e.g. during an incident) can identify which
 * comanda this event is about without joining back to {@code orders}.
 */
public record OrderReadyEvent(UUID orderId, int commandNumber) {

    public static OrderReadyEvent from(OrderWithItems result) {
        return new OrderReadyEvent(result.order().getId(), result.order().getCommand().getNumber());
    }

}
