package com.farelo.api.cashier;

import java.util.UUID;

/**
 * Thrown when an operation requires a {@link CashSession} to be {@link
 * CashSessionStatus#OPEN} but it currently isn't ({@code CLOSED}). See
 * {@link CashSessionService#findOpen(UUID)} — called both by {@link
 * CashSessionService#close} (FARELO-308) and by {@code
 * CashMovementService} (FARELO-310/311, sangria/suprimento/despesa/receita
 * all require an open session).
 */
public class CashSessionNotOpenException extends RuntimeException {

    private final UUID cashSessionId;
    private final CashSessionStatus currentStatus;

    public CashSessionNotOpenException(UUID cashSessionId, CashSessionStatus currentStatus) {
        super("Cash session %s is not open (current status: %s)".formatted(cashSessionId, currentStatus));
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
