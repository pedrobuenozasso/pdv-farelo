package com.farelo.api.ordering;

import java.util.UUID;

/**
 * Service-layer input for one line item when creating an {@link Order} —
 * decoupled from the web-layer {@code OrderItemRequest} DTO, same as every
 * other service in this codebase takes plain values/domain types rather
 * than {@code @Valid}-annotated request DTOs (see {@code ProductService},
 * {@code CommandService}).
 */
public record NewOrderItem(UUID productId, int quantity) {
}
