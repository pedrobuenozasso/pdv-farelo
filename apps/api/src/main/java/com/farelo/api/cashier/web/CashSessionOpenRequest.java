package com.farelo.api.cashier.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/cash-sessions} (FARELO-307). Never
 * expose the JPA entity directly on the API (see AGENTS.md).
 *
 * <p>No operator field — the acting operator is always the authenticated
 * caller ({@code AuthenticatedPrincipal#userId()}, resolved by {@code
 * CashSessionController#open}), same "actor comes from the token, not the
 * request body" convention {@code PaymentController#record} already
 * established for {@code actorId}.
 */
public record CashSessionOpenRequest(@NotNull UUID shiftId, @NotNull @PositiveOrZero BigDecimal openingAmount) {
}
