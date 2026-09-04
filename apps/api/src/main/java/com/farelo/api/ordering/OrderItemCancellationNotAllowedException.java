package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Thrown when {@link OrderService#cancelItem} targets an item whose parent
 * {@link Order} is in a terminal status ({@code DELIVERED} or {@code
 * CANCELLED}) — cancelling one line of an order the customer already
 * received doesn't make operational sense (can't un-hand-over a coxinha),
 * and an order already {@code CANCELLED} as a whole has nothing left to
 * cancel piecemeal. Every non-terminal order status ({@code CREATED},
 * {@code CONFIRMED}, {@code PREPARING}, {@code READY}) accepts an item
 * cancellation — deliberately wider than {@code
 * OrderService#CANCELLABLE_STATUSES} needs to be for whole-order
 * cancellation would be if it excluded any of those, since this is a
 * different, item-level question ("has this order been fully settled
 * yet?"), not the same one.
 */
public class OrderItemCancellationNotAllowedException extends RuntimeException {

    private final UUID orderId;
    private final UUID itemId;
    private final OrderStatus orderStatus;

    public OrderItemCancellationNotAllowedException(
            UUID orderId, UUID itemId, OrderStatus orderStatus) {
        super("Order item %s cannot be cancelled: order %s is %s".formatted(
                itemId, orderId, orderStatus));
        this.orderId = orderId;
        this.itemId = itemId;
        this.orderStatus = orderStatus;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

}
