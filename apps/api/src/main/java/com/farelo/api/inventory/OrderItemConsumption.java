package com.farelo.api.inventory;

import java.util.UUID;

/**
 * Service-layer input for one sold line item when consuming a {@link Recipe}
 * on order creation (FARELO-096, {@link InventoryMovementService#consumeForOrder}).
 * Deliberately not {@code com.farelo.api.ordering.OrderItem} itself — the
 * {@code inventory} package must not depend on {@code ordering} (the
 * dependency direction already established across this codebase is the
 * other way around: {@code ordering.OrderService} depends on {@code
 * catalog.ProductService}/{@code inventory} services, never the reverse —
 * see {@code CommandOrdersController}'s javadoc for the same
 * "keep the dependency unidirectional" reasoning applied to a different pair
 * of packages). This record is the {@code inventory} domain's own minimal
 * shape of "a product was sold N times", the same pattern {@code
 * ordering.NewOrderItem} already uses to decouple a service's input from
 * another domain's entity.
 */
public record OrderItemConsumption(UUID productId, int quantity) {
}
