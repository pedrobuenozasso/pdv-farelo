package com.farelo.api.ordering.web;

import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/orders/{id}/waiter} (FARELO-314).
 * {@code userId} is deliberately not {@code @NotNull} — a {@code null}
 * (or omitted) value clears the assignment ({@code
 * OrderService#unassignWaiter}), a non-null one assigns/reassigns it
 * ({@code OrderService#assignWaiter}). See {@code OrderController#assignWaiter}
 * for which of the two this maps to.
 */
public record AssignWaiterRequest(UUID userId) {
}
