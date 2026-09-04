package com.farelo.api.payment.web;

import com.farelo.api.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/commands/{number}/payments}
 * (FARELO-141, "Registrar pagamento manual"). Never expose the JPA entity
 * directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>No {@code commandNumber}/{@code command} field — comes from the URL
 * path, not the body (the endpoint is already scoped to one comanda), same
 * convention as {@link com.farelo.api.inventory.web.InventoryMovementRequest}
 * not repeating {@code ingredientId}.
 *
 * <p>{@code amount} must be strictly positive ({@code @Positive}, same
 * validation-layer pattern as {@code InventoryMovementRequest#quantity()}/
 * {@code InventoryLossRequest#quantity()}): a payment of zero or less isn't a
 * payment. Unlike those two, there is no server-side sign flip to reason
 * about here — {@link com.farelo.api.payment.Payment#getAmount()} stores
 * exactly this positive magnitude, verbatim.
 *
 * <p>{@code method} is required, {@code @NotNull} — a missing/explicit-{@code
 * null} JSON field deserializes to {@code null} on the record (an enum is
 * already a reference type, so there's no wrapper-vs-primitive gotcha to
 * consider here, same reasoning as {@link
 * com.farelo.api.catalog.web.ProductRequest}'s javadoc for {@code
 * productionStation}), which {@code @NotNull} then rejects.
 */
public record PaymentRequest(@NotNull @Positive BigDecimal amount, @NotNull PaymentMethod method) {
}
