package com.farelo.api.cashier;

/**
 * Lifecycle states of a {@link CashSession} (Caixa). Two values, mirroring
 * the two real states the ticket text describes (FARELO-307/308): a
 * session is open from the moment it's created until it's closed. There is
 * no third "reconciled" lifecycle state — reconciliation is tracked
 * separately by {@link CashSessionReconciliationStatus}, since a closed
 * session stays {@code CLOSED} forever regardless of whether it later
 * turns out to match or not.
 */
public enum CashSessionStatus {
    OPEN,
    CLOSED
}
