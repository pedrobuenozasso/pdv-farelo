package com.farelo.api.payment.web;

import com.farelo.api.payment.PaymentBalance;

import java.math.BigDecimal;

/**
 * Response body for {@code GET /api/v1/commands/{number}/payments/balance}
 * (FARELO-223, extended by FARELO-230/231/232 with {@code totalDiscount}).
 * {@code commandNumber} included on purpose, same self-describing
 * convention {@link PaymentResponse}/{@link PaymentTotalResponse} already
 * follow.
 */
public record PaymentBalanceResponse(
        int commandNumber,
        BigDecimal totalOwed,
        BigDecimal totalDiscount,
        BigDecimal totalPaid,
        BigDecimal remaining) {

    public static PaymentBalanceResponse from(int commandNumber, PaymentBalance balance) {
        return new PaymentBalanceResponse(
                commandNumber,
                balance.totalOwed(),
                balance.totalDiscount(),
                balance.totalPaid(),
                balance.remaining());
    }

}
