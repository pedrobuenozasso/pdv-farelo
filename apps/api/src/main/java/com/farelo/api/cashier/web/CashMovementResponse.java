package com.farelo.api.cashier.web;

import com.farelo.api.cashier.CashMovement;
import com.farelo.api.cashier.CashMovementType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link CashMovement} —
 * the JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code cashSessionId}, not a nested {@code CashSessionResponse} — same
 * flattening convention {@code PaymentResponse#commandNumber} uses:
 * nothing today needs the full session detail alongside a movement, only
 * which one it belongs to. Reading {@code cashSession.getId()} is always
 * safe even on a lazy, uninitialized proxy — JPA resolves an association's
 * id straight from the owning FK column, no extra query/initialization
 * needed (see {@code CashMovementRepository}'s javadoc for why no
 * {@code JOIN FETCH} was needed for this query in the first place).
 */
public record CashMovementResponse(
        UUID id,
        UUID cashSessionId,
        CashMovementType type,
        BigDecimal amount,
        String description,
        UUID createdByUserId,
        String createdByUserName,
        OffsetDateTime createdAt) {

    public static CashMovementResponse from(CashMovement movement) {
        return new CashMovementResponse(
                movement.getId(),
                movement.getCashSession().getId(),
                movement.getType(),
                movement.getAmount(),
                movement.getDescription(),
                movement.getCreatedByUserId(),
                movement.getCreatedByUserName(),
                movement.getCreatedAt());
    }

}
