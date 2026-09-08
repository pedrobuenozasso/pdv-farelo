package com.farelo.api.cashier.web;

import com.farelo.api.cashier.CashSession;
import com.farelo.api.cashier.CashSessionReconciliationStatus;
import com.farelo.api.cashier.CashSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link CashSession} —
 * the JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code shiftId}/{@code shiftName} rather than a nested {@code
 * ShiftResponse} — same flattening convention {@link
 * com.farelo.api.payment.web.PaymentResponse} uses for {@code
 * commandNumber} (a comanda's business identifier, not a nested object):
 * nothing today needs the Shift's full detail (account names) alongside a
 * cash session, only which one it is.
 *
 * <p>Every closing/reconciliation field is included even when {@code null}
 * (a session still {@code OPEN}/not yet reconciled) — same "always the
 * same shape" convention every other response DTO in this codebase
 * follows, rather than a conditionally-shaped JSON body.
 */
public record CashSessionResponse(
        UUID id,
        UUID shiftId,
        String shiftName,
        UUID openedByUserId,
        String openedByUserName,
        OffsetDateTime openedAt,
        CashSessionStatus status,
        BigDecimal closingAmount,
        OffsetDateTime closedAt,
        UUID closedByUserId,
        String closedByUserName,
        CashSessionReconciliationStatus reconciliationStatus,
        BigDecimal expectedAmount,
        BigDecimal reconciliationDifference,
        OffsetDateTime reconciledAt,
        UUID reconciledByUserId,
        String reconciledByUserName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CashSessionResponse from(CashSession session) {
        return new CashSessionResponse(
                session.getId(),
                session.getShift().getId(),
                session.getShift().getName(),
                session.getOpenedByUserId(),
                session.getOpenedByUserName(),
                session.getOpenedAt(),
                session.getStatus(),
                session.getClosingAmount(),
                session.getClosedAt(),
                session.getClosedByUserId(),
                session.getClosedByUserName(),
                session.getReconciliationStatus(),
                session.getExpectedAmount(),
                session.getReconciliationDifference(),
                session.getReconciledAt(),
                session.getReconciledByUserId(),
                session.getReconciledByUserName(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

}
