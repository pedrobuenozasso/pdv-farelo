package com.farelo.api.cashier;

/**
 * Reconciliation outcome of a {@link CashSession} (FARELO-309, "Conferência
 * de caixa") — independent of {@link CashSessionStatus}: a session becomes
 * eligible for reconciliation once {@code CLOSED}, but reconciling it is a
 * separate step (per {@code docs/gaps-sischef-pdv.md}: "passo separado do
 * fechamento"), never a side effect of closing.
 *
 * <p>{@code PENDING} (the default from open through close) means "not
 * reconciled yet". {@link CashSessionService#reconcile} moves a session to
 * exactly one of {@code MATCHED}/{@code MISMATCHED}, once, comparing the
 * informed closing amount against the system-calculated expected amount —
 * see that method's javadoc for the full comparison.
 */
public enum CashSessionReconciliationStatus {
    PENDING,
    MATCHED,
    MISMATCHED
}
