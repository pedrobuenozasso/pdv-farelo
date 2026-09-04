package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Thrown when {@code itemId} doesn't resolve to an {@link OrderItem}
 * belonging to {@code orderId} — either the id doesn't exist at all, or it
 * exists but belongs to a <em>different</em> order (a cross-order
 * reference is treated as "not found" here too, not silently acted on;
 * same convention {@code RecipeItemNotFoundException} already established
 * for a cross-recipe {@code itemId}).
 */
public class OrderItemNotFoundException extends RuntimeException {

    private final UUID orderId;
    private final UUID itemId;

    public OrderItemNotFoundException(UUID orderId, UUID itemId) {
        super("Order item not found: %s (order %s)".formatted(itemId, orderId));
        this.orderId = orderId;
        this.itemId = itemId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getItemId() {
        return itemId;
    }

}
