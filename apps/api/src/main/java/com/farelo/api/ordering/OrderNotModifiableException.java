package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Thrown when an operation that adds/changes an {@link Order}'s items
 * (FARELO-318, {@link OrderService#duplicateItem}) targets an order in a
 * terminal status ({@code DELIVERED} or {@code CANCELLED}) — same {@link
 * OrderService#ITEM_CANCELLATION_BLOCKED_STATUSES} set {@link
 * OrderService#cancelItem} already uses for the analogous "has this order
 * been fully settled yet?" question, just for a different action.
 *
 * <p>A distinct exception/code from {@link
 * OrderItemCancellationNotAllowedException} rather than reusing it — that
 * one is specifically about <em>cancelling</em> an item; this is about
 * <em>adding</em> one. Conflating the two under one name would make a 409
 * response for "can't duplicate" read as if a cancellation had been
 * attempted.
 */
public class OrderNotModifiableException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus orderStatus;

    public OrderNotModifiableException(UUID orderId, OrderStatus orderStatus) {
        super("Order %s cannot be modified: it is %s".formatted(orderId, orderStatus));
        this.orderId = orderId;
        this.orderStatus = orderStatus;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

}
