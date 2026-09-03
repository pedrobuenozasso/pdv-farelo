package com.farelo.api.payment.web;

import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Payment} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code commandNumber}, not the command's UUID {@code id} — same
 * identifier convention {@link com.farelo.api.ordering.web.OrderResponse}
 * already follows, even though the enclosing {@code GET
 * /api/v1/commands/{number}/payments} URL already carries the number: it
 * keeps the response self-describing on its own, without requiring a
 * caller to correlate back to the request URL. No {@code updatedAt} field:
 * a {@code Payment} has no such concept — see {@link Payment}'s javadoc for
 * why (append-only, like {@code InventoryMovement}/{@code AuditLog}).
 */
public record PaymentResponse(
        UUID id,
        int commandNumber,
        BigDecimal amount,
        PaymentMethod method,
        OffsetDateTime createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getCommand().getNumber(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getCreatedAt());
    }

}
