package com.farelo.api.cashier.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body shared by {@code POST /api/v1/cash-sessions/{id}/withdrawals}
 * (FARELO-310), {@code .../supplies} (FARELO-310), {@code .../expenses}
 * (FARELO-311) and {@code .../incomes} (FARELO-311) — the {@code type} is
 * fixed by which URL is called, not a field in this body (see {@code
 * CashMovementController}'s javadoc for why these are four dedicated
 * endpoints rather than one generic endpoint with a discriminator field).
 * Never expose the JPA entity directly on the API (see AGENTS.md).
 *
 * <p>{@code amount} strictly positive ({@code @Positive}), same convention
 * as {@code PaymentRequest#amount()} — the stored magnitude is always
 * positive, direction comes from the endpoint/{@code type}. {@code
 * description} is optional, same convention as {@code
 * DiscountRequest#reason()}.
 */
public record CashMovementRequest(
        @NotNull @Positive BigDecimal amount,
        @Size(max = 500, message = "description deve ter no máximo 500 caracteres") String description) {
}
