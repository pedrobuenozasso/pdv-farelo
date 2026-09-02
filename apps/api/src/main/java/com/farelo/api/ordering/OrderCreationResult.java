package com.farelo.api.ordering;

import java.util.List;

/**
 * Result of {@link OrderService#create(int, List)}: the created
 * {@link Order} together with its {@link OrderItem}s. {@code Order} does
 * not hold a collection of its items (kept minimal in FARELO-050/051), so
 * this is just a transfer object for the web layer to build the response
 * from — never serialized directly (see
 * {@code com.farelo.api.ordering.web.OrderResponse}).
 */
public record OrderCreationResult(Order order, List<OrderItem> items) {
}
