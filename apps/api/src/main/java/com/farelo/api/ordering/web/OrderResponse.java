package com.farelo.api.ordering.web;

import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.ordering.OrderWithItems;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/orders} — the JPA entity is never
 * exposed directly (see AGENTS.md). {@code commandNumber}, not the
 * command's UUID {@code id}, matching the same identifier convention used
 * throughout {@code com.farelo.api.command}.
 */
public record OrderResponse(
        UUID id,
        int commandNumber,
        OrderStatus status,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt) {

    public static OrderResponse from(OrderWithItems result) {
        Order order = result.order();
        List<OrderItemResponse> items = result.items().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCommand().getNumber(),
                order.getStatus(),
                items,
                order.getCreatedAt());
    }

}
