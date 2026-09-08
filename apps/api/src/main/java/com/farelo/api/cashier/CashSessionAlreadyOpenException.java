package com.farelo.api.cashier;

import java.util.UUID;

/**
 * Thrown by {@link CashSessionService#open} when the acting operator
 * already has an {@link CashSessionStatus#OPEN} {@link CashSession} —
 * FARELO-307's "um caixa aberto por vez por operador" rule. Deliberately
 * scoped to the operator, not the {@link Shift}: the same template can be
 * in concurrent use by different operators (see {@link CashSessionService}'s
 * javadoc for the full reasoning).
 */
public class CashSessionAlreadyOpenException extends RuntimeException {

    private final UUID operatorId;
    private final UUID existingCashSessionId;

    public CashSessionAlreadyOpenException(UUID operatorId, UUID existingCashSessionId) {
        super("Operator %s already has an open cash session: %s".formatted(operatorId, existingCashSessionId));
        this.operatorId = operatorId;
        this.existingCashSessionId = existingCashSessionId;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public UUID getExistingCashSessionId() {
        return existingCashSessionId;
    }

}
