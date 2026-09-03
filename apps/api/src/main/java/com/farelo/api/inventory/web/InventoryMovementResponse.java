package com.farelo.api.inventory.web;

import com.farelo.api.inventory.InventoryMovement;
import com.farelo.api.inventory.InventoryMovementType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link InventoryMovement}
 * — the JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code orderId} is nullable in the response, mirroring the entity —
 * present only for order-sourced movement types (see {@link
 * InventoryMovement}'s javadoc), {@code null} for every other type. No
 * {@code updatedAt} field: this ledger row has no such concept — see {@link
 * InventoryMovement}'s javadoc for why.
 */
public record InventoryMovementResponse(
        UUID id,
        UUID ingredientId,
        BigDecimal quantity,
        InventoryMovementType type,
        UUID orderId,
        OffsetDateTime createdAt) {

    public static InventoryMovementResponse from(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getIngredient().getId(),
                movement.getQuantity(),
                movement.getType(),
                movement.getOrderId(),
                movement.getCreatedAt());
    }

}
