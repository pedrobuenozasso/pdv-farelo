package com.farelo.api.ordering.web;

import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemCancelReason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for one order line item — the JPA entity is never exposed
 * directly (see AGENTS.md). {@code unitPrice} is the frozen snapshot
 * captured at creation, not the product's current price.
 *
 * <p>{@code cancelled}/{@code cancelledAt}/{@code cancelledByUserName}/
 * {@code cancelReason}/{@code cancelDescription} (FARELO-200/201): all
 * {@code null}/{@code false} for a never-cancelled item. {@code cancelled}
 * is included explicitly (not left for the client to infer from {@code
 * cancelledAt != null}) since a plain boolean is the more natural read for
 * "should this line count toward the comanda's total" — the recalculation
 * FARELO-200 itself asks for ("recalcular valor da comanda"), which the
 * frontend performs client-side the same way it already excludes whole
 * CANCELLED orders (see apps/web/src/app/pdv/page.tsx).
 * {@code cancelledByUserId} is deliberately NOT exposed here — the id is
 * an internal detail with no UI use yet; only the human-readable name is.
 */
public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        boolean cancelled,
        OffsetDateTime cancelledAt,
        String cancelledByUserName,
        OrderItemCancelReason cancelReason,
        String cancelDescription) {

    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProduct().getId(),
                orderItem.getProduct().getName(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice(),
                orderItem.isCancelled(),
                orderItem.getCancelledAt(),
                orderItem.getCancelledByUserName(),
                orderItem.getCancelReason(),
                orderItem.getCancelDescription());
    }

}
