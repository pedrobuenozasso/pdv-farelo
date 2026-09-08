package com.farelo.api.ordering;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload for the {@code OrderCreated} outbox event (FARELO-060), published
 * by {@link OrderService#create(int, List)} in the same transaction as the
 * order/items it describes. Lives in {@code ordering}, not {@code
 * com.farelo.api.outbox} — event payload shapes belong to the domain that
 * produces them (see the outbox package's package-info, "dependency
 * direction").
 *
 * <p>Deliberately simple (order id, command number, and each item's
 * product/quantity/price snapshot) — no real consumer exists yet (the
 * outbox worker is still a stub); this only proves the publish mechanism
 * against a real use case, not a finished event contract for future
 * consumers to build against.
 *
 * <p>{@code commandNumber} is nullable as of FARELO-312 — {@code null} for
 * a {@code BALCAO} order (see {@link Order}'s javadoc, "Design decision —
 * BALCAO"), which has no {@link com.farelo.api.command.Command} to name.
 */
public record OrderCreatedEvent(UUID orderId, Integer commandNumber, List<Item> items) {

    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {
    }

    public static OrderCreatedEvent from(OrderWithItems result) {
        List<Item> items = result.items().stream()
                .map(item -> new Item(item.getProduct().getId(), item.getQuantity(), item.getUnitPrice()))
                .toList();

        Order order = result.order();
        Integer commandNumber = order.getCommand() != null ? order.getCommand().getNumber() : null;

        return new OrderCreatedEvent(order.getId(), commandNumber, items);
    }

}
