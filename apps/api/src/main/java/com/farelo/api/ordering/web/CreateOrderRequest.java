package com.farelo.api.ordering.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/orders}.
 *
 * <p>{@code customerName}/{@code customerPhone} are optional and carry no
 * format validation — this is a plain snapshot of contact info for staff
 * (PDV/KDS), not an identity check, so a phone-format validator would be
 * YAGNI right now (see {@link com.farelo.api.ordering.Order}'s javadoc).
 * Persisting them is deliberately still not a {@code customer} domain —
 * see docs/domain-model.md.
 *
 * <p>{@code commandNumber} is {@code Integer} (wrapper), not a primitive
 * {@code int}: a missing field must fail validation with a clear 400
 * ({@code @NotNull}), not silently default to {@code 0} and produce a
 * confusing 404 ("Command not found: 0") instead.
 */
public record CreateOrderRequest(
        @NotNull Integer commandNumber,
        @NotEmpty @Valid List<OrderItemRequest> items,
        String customerName,
        String customerPhone) {
}
