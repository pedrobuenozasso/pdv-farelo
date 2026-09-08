package com.farelo.api.discount;

import java.util.UUID;

/**
 * Thrown when an item-level discount (FARELO-317) is requested against an
 * {@code OrderItem} whose parent {@code Order} has no {@code Command} —
 * i.e. a {@code BALCAO} order (see {@code
 * com.farelo.api.ordering.Order}'s javadoc, "Design decision — BALCAO",
 * FARELO-312). A {@link Discount} row always denormalizes {@code command}
 * (required, {@code NOT NULL} — see this class's own javadoc), so there is
 * no comanda to attach an item-scoped discount to for that kind of order.
 *
 * <p>Same known-gap posture as {@code Order}'s own javadoc already
 * documents for {@code payment}: a real fix needs a discount (and payment)
 * path that doesn't require a comanda number, which is out of scope for
 * this ticket.
 */
public class ItemDiscountNotSupportedException extends RuntimeException {

    private final UUID orderId;

    public ItemDiscountNotSupportedException(UUID orderId) {
        super("Order %s has no comanda — item-level discounts aren't supported for BALCAO orders yet".formatted(
                orderId));
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

}
