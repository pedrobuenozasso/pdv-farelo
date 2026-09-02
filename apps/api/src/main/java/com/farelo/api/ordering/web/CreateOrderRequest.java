package com.farelo.api.ordering.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/orders}.
 *
 * <p>No customer name/phone here — those fields stay client-side only for
 * now (FARELO-045); persisting customer data belongs to a {@code customer}
 * domain that no ticket has created yet, so this ticket doesn't invent it.
 *
 * <p>{@code commandNumber} is {@code Integer} (wrapper), not a primitive
 * {@code int}: a missing field must fail validation with a clear 400
 * ({@code @NotNull}), not silently default to {@code 0} and produce a
 * confusing 404 ("Command not found: 0") instead.
 */
public record CreateOrderRequest(
        @NotNull Integer commandNumber,
        @NotEmpty @Valid List<OrderItemRequest> items) {
}
