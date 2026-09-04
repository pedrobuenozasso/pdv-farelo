package com.farelo.api.payment;

import java.math.BigDecimal;

/**
 * A comanda's payment balance (FARELO-223, "Calcular saldo restante") —
 * {@code totalOwed} ({@link com.farelo.api.ordering.OrderService#getTotalOwed(int)}),
 * {@code totalDiscount} ({@link com.farelo.api.discount.DiscountService#getTotalDiscount(int)},
 * folded in by FARELO-230/231/232) and {@code totalPaid} ({@link
 * PaymentService#getTotalPaid(int)}), plus {@code remaining}, computed here
 * rather than left for a caller to derive: the ticket's own requirement is
 * explicit ("Não usar cálculo somente no frontend. Backend deve ser fonte
 * de verdade") — before this class, {@code apps/web}'s PDV screen computed
 * {@code totalOwed} itself by summing every order's items client-side, and
 * {@code remaining} by subtracting {@code totalPaid} from that, duplicating
 * logic the backend already has the real data for.
 *
 * <p>{@code totalOwed} deliberately keeps its original, pre-FARELO-230/231
 * meaning — the raw sum of order items, untouched by discounts (still owned
 * entirely by {@code OrderService}, which has no reason to know discounts
 * exist). {@code remaining = max(totalOwed - totalDiscount - totalPaid, 0)}
 * — same floor-at-zero semantics this record already had before discounts
 * existed (an overpaid, or now also an over-discounted, comanda reports
 * {@code 0} owed, never a negative "owes less than nothing").
 */
public record PaymentBalance(
        BigDecimal totalOwed, BigDecimal totalDiscount, BigDecimal totalPaid, BigDecimal remaining) {

    public static PaymentBalance of(BigDecimal totalOwed, BigDecimal totalDiscount, BigDecimal totalPaid) {
        BigDecimal remaining = totalOwed.subtract(totalDiscount).subtract(totalPaid).max(BigDecimal.ZERO);
        return new PaymentBalance(totalOwed, totalDiscount, totalPaid, remaining);
    }

}
