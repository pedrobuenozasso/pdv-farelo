package com.farelo.api.payment.web;

import java.math.BigDecimal;

/**
 * Response body for {@code GET /api/v1/commands/{number}/payments/total}
 * (FARELO-142). Analogous to {@code IngredientBalanceResponse}
 * (FARELO-095) — a dedicated response shape for a derived aggregate,
 * separate from the ledger listing it's computed from ({@code
 * PaymentResponse}/the {@code GET .../payments} list).
 *
 * <p>{@code commandNumber} is included on purpose, same self-describing
 * convention {@link PaymentResponse} already follows, even though the
 * enclosing URL already carries the number: a caller reading just the body
 * (e.g. logged separately from the request) doesn't need to correlate back
 * to the request URL to know which comanda this total belongs to.
 *
 * <p>Deliberately no "total owed"/"fully paid" field here — see {@code
 * PaymentService#getTotalPaid}'s javadoc for why that comparison is out of
 * this ticket's scope (FARELO-143's job instead).
 */
public record PaymentTotalResponse(int commandNumber, BigDecimal totalPaid) {

    public static PaymentTotalResponse of(int commandNumber, BigDecimal totalPaid) {
        return new PaymentTotalResponse(commandNumber, totalPaid);
    }

}
