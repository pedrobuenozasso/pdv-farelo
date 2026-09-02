package com.farelo.api.ordering;

import java.util.List;

/**
 * An {@link Order} together with its {@link OrderItem}s. {@code Order}
 * does not hold a collection of its items (kept minimal in
 * FARELO-050/051), so this is just a transfer object for the web layer to
 * build responses from — never serialized directly (see
 * {@code com.farelo.api.ordering.web.OrderResponse}).
 *
 * <p>Used both by {@link OrderService#create(int, List)} (FARELO-052/053)
 * and {@link OrderService#listByCommand(int)} (FARELO-055) — renamed from
 * {@code OrderCreationResult} when the second use case appeared, since
 * "creation result" stopped being an accurate name.
 */
public record OrderWithItems(Order order, List<OrderItem> items) {
}
