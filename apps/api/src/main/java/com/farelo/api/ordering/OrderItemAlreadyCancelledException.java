package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Thrown when {@link OrderService#cancelItem} targets an {@link OrderItem}
 * that's already cancelled ({@code getCancelledAt() != null}) — cancelling
 * twice would silently overwrite the original operator/reason/timestamp,
 * which reads as a data-integrity bug, not an idempotent no-op (unlike,
 * say, re-sending an already-{@code SENT} notification, which the
 * notification domain deliberately does allow).
 */
public class OrderItemAlreadyCancelledException extends RuntimeException {

    private final UUID itemId;

    public OrderItemAlreadyCancelledException(UUID itemId) {
        super("Order item already cancelled: " + itemId);
        this.itemId = itemId;
    }

    public UUID getItemId() {
        return itemId;
    }

}
