package com.farelo.api.ordering.web;

import com.farelo.api.ordering.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response body for one order line item — the JPA entity is never exposed
 * directly (see AGENTS.md). {@code unitPrice} is the frozen snapshot
 * captured at creation, not the product's current price.
 */
public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice) {

    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProduct().getId(),
                orderItem.getProduct().getName(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice());
    }

}
