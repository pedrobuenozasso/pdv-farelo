package com.farelo.api.cashier.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/cash-sessions/{id}/close}
 * (FARELO-308). Never expose the JPA entity directly on the API (see
 * AGENTS.md).
 *
 * <p>{@code closingAmount} allows zero ({@code @PositiveOrZero}, not
 * {@code @Positive}) — unlike {@code PaymentRequest#amount()}, a session
 * can legitimately close with no physical cash counted at all (every sale
 * that shift was card/PIX), so zero is a real, valid closing amount, not a
 * validation error.
 */
public record CashSessionCloseRequest(@NotNull @PositiveOrZero BigDecimal closingAmount) {
}
