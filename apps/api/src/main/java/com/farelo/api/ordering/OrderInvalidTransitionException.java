package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Thrown when an operation requires an {@link Order} to be in a specific
 * status before transitioning to a new one, but its current status
 * doesn't match (FARELO-057/058: {@code CREATED}→{@code PREPARING},
 * {@code PREPARING}→{@code READY}).
 *
 * <p>Deliberately a single reusable exception for both transitions,
 * unlike {@code CommandNotAvailableException}/
 * {@code CommandCannotBeClosedException} in {@code com.farelo.api.command}
 * (FARELO-033/034), which had to be separate: reusing "not available"'s
 * wording for {@code close} would have read backwards for its most common
 * failure case (a command still {@code AVAILABLE} <em>is</em> available —
 * that's exactly why it can't be closed). No such ambiguity here: the
 * message explicitly names both the attempted target status and the
 * required origin status, so it reads correctly regardless of which
 * transition triggered it — nothing to avoid by splitting the class.
 */
public class OrderInvalidTransitionException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    public OrderInvalidTransitionException(UUID orderId, OrderStatus currentStatus, OrderStatus targetStatus) {
        super("Order %s cannot transition to %s (current status: %s)"
                .formatted(orderId, targetStatus, currentStatus));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }

}
