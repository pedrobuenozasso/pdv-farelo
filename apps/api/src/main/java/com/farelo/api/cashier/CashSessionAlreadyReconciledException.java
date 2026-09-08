package com.farelo.api.cashier;

import java.util.UUID;

/**
 * Thrown by {@link CashSessionService#reconcile} (FARELO-309) when the
 * target {@link CashSession} has already been reconciled (its {@link
 * CashSessionReconciliationStatus} is no longer {@code PENDING}).
 * Reconciliation runs exactly once per session — a wrong reconciliation is
 * corrected operationally (same spirit as this codebase's append-only
 * ledgers never being edited in place), not re-run.
 */
public class CashSessionAlreadyReconciledException extends RuntimeException {

    private final UUID cashSessionId;

    public CashSessionAlreadyReconciledException(UUID cashSessionId) {
        super("Cash session already reconciled: " + cashSessionId);
        this.cashSessionId = cashSessionId;
    }

    public UUID getCashSessionId() {
        return cashSessionId;
    }

}
