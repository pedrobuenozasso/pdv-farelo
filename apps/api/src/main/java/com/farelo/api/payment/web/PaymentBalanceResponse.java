package com.farelo.api.payment.web;

import com.farelo.api.payment.PaymentBalance;

import java.math.BigDecimal;

/**
 * Response body for {@code GET /api/v1/commands/{number}/payments/balance}
 * (FARELO-223). {@code commandNumber} included on purpose, same
 * self-describing convention {@link PaymentResponse}/{@link
 * PaymentTotalResponse} already follow.
 */
public record PaymentBalanceResponse(
        int commandNumber, BigDecimal totalOwed, BigDecimal totalPaid, BigDecimal remaining) {

    public static PaymentBalanceResponse from(int commandNumber, PaymentBalance balance) {
        return new PaymentBalanceResponse(
                commandNumber, balance.totalOwed(), balance.totalPaid(), balance.remaining());
    }

}
