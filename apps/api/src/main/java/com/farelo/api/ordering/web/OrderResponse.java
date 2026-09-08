package com.farelo.api.ordering.web;

import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.ordering.OrderType;
import com.farelo.api.ordering.OrderWithItems;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/orders} — the JPA entity is never
 * exposed directly (see AGENTS.md). {@code commandNumber}, not the
 * command's UUID {@code id}, matching the same identifier convention used
 * throughout {@code com.farelo.api.command}.
 *
 * <p>{@code commandNumber} is nullable as of FARELO-312 — {@code null} for
 * a {@code BALCAO} {@code type}, which has no comanda (see {@link
 * Order}'s javadoc, "Design decision — BALCAO"). {@code type} is included
 * explicitly so a client can tell the two shapes apart without inferring
 * it from {@code commandNumber} being absent.
 *
 * <p>{@code assignedUserId}/{@code assignedUserName} (FARELO-314): unlike
 * {@code OrderItemResponse.cancelledByUserId} (deliberately not exposed —
 * a historical fact with no edit affordance), this pair is exposed in
 * full, id included — the id is what a PDV client needs to pre-select the
 * current waiter in an editable picker, not just display a name.
 */
public record OrderResponse(
        UUID id,
        OrderType type,
        Integer commandNumber,
        OrderStatus status,
        List<OrderItemResponse> items,
        String customerName,
        String customerPhone,
        UUID assignedUserId,
        String assignedUserName,
        String note,
        boolean printOnConfirm,
        OffsetDateTime createdAt) {

    public static OrderResponse from(OrderWithItems result) {
        Order order = result.order();
        List<OrderItemResponse> items = result.items().stream()
                .map(OrderItemResponse::from)
                .toList();

        Integer commandNumber = order.getCommand() != null ? order.getCommand().getNumber() : null;

        return new OrderResponse(
                order.getId(),
                order.getType(),
                commandNumber,
                order.getStatus(),
                items,
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getAssignedUserId(),
                order.getAssignedUserName(),
                order.getNote(),
                order.isPrintOnConfirm(),
                order.getCreatedAt());
    }

}
