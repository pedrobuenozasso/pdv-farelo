package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Thrown when an operation references an {@link Order} {@code id} that
 * does not exist. Same pattern as {@code CategoryNotFoundException}/
 * {@code ProductNotFoundException}/{@code CommandNotFoundException},
 * keyed by the technical {@code id} (UUID) rather than a human-facing
 * number — unlike {@code Command}, orders don't have a business-facing
 * sequential identifier; they're always looked up by {@code id}.
 */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

}
