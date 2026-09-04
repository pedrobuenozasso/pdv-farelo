package com.farelo.api.payment.web;

import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/commands/{number}/payments}
 * (FARELO-141), extended by FARELO-225 ("Tratar troco em dinheiro") with
 * {@code changeGiven} — same fields {@link PaymentResponse} exposes, plus
 * that one extra. A dedicated response type rather than adding {@code
 * changeGiven} to {@link PaymentResponse} itself: {@code changeGiven} is
 * only ever meaningful for the single payment just recorded (it's cash
 * physically handed back, never persisted — see {@code
 * PaymentRequest}'s javadoc), so every other {@link PaymentResponse}
 * consumer ({@code GET .../payments}, the historical ledger listing) would
 * otherwise carry a field that's always {@code null} for every row it
 * returns. Same "dedicated response shape per distinct read" precedent
 * {@link PaymentTotalResponse}/{@link PaymentBalanceResponse} already
 * follow.
 */
public record PaymentRecordResponse(
        UUID id,
        int commandNumber,
        BigDecimal amount,
        PaymentMethod method,
        OffsetDateTime createdAt,
        BigDecimal changeGiven) {

    public static PaymentRecordResponse from(Payment payment, BigDecimal changeGiven) {
        return new PaymentRecordResponse(
                payment.getId(),
                payment.getCommand().getNumber(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getCreatedAt(),
                changeGiven);
    }

}
