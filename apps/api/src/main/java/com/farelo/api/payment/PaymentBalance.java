package com.farelo.api.payment;

import java.math.BigDecimal;

/**
 * A comanda's payment balance (FARELO-223, "Calcular saldo restante") —
 * {@code totalOwed} ({@link com.farelo.api.ordering.OrderService#getTotalOwed(int)})
 * and {@code totalPaid} ({@link PaymentService#getTotalPaid(int)}), plus
 * {@code remaining}, computed here rather than left for a caller to derive:
 * the ticket's own requirement is explicit ("Não usar cálculo somente no
 * frontend. Backend deve ser fonte de verdade") — before this class, {@code
 * apps/web}'s PDV screen computed {@code totalOwed} itself by summing every
 * order's items client-side, and {@code remaining} by subtracting {@code
 * totalPaid} from that, duplicating logic the backend already has the real
 * data for.
 *
 * <p>{@code remaining = max(totalOwed - totalPaid, 0)} — same floor-at-zero
 * semantics the PDV frontend already applied before this ticket (an
 * overpaid comanda reports {@code 0} owed, never a negative "owes less than
 * nothing"), now computed once, here, instead of by every caller.
 */
public record PaymentBalance(BigDecimal totalOwed, BigDecimal totalPaid, BigDecimal remaining) {

    public static PaymentBalance of(BigDecimal totalOwed, BigDecimal totalPaid) {
        BigDecimal remaining = totalOwed.subtract(totalPaid).max(BigDecimal.ZERO);
        return new PaymentBalance(totalOwed, totalPaid, remaining);
    }

}
