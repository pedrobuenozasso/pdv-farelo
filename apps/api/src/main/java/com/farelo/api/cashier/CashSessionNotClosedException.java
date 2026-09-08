package com.farelo.api.cashier;

import java.util.UUID;

/**
 * Thrown by {@link CashSessionService#reconcile} (FARELO-309) when the
 * target {@link CashSession} isn't {@link CashSessionStatus#CLOSED} yet —
 * conferência is a separate step that only makes sense after the caixa has
 * been closed (informed a closing amount).
 */
public class CashSessionNotClosedException extends RuntimeException {

    private final UUID cashSessionId;
    private final CashSessionStatus currentStatus;

    public CashSessionNotClosedException(UUID cashSessionId, CashSessionStatus currentStatus) {
        super("Cash session %s is not closed yet (current status: %s)".formatted(cashSessionId, currentStatus));
        this.cashSessionId = cashSessionId;
        this.currentStatus = currentStatus;
    }

    public UUID getCashSessionId() {
        return cashSessionId;
    }

    public CashSessionStatus getCurrentStatus() {
        return currentStatus;
    }

}
