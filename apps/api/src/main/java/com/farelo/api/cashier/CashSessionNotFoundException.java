package com.farelo.api.cashier;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link CashSession} id that does
 * not exist.
 */
public class CashSessionNotFoundException extends RuntimeException {

    private final UUID cashSessionId;

    public CashSessionNotFoundException(UUID cashSessionId) {
        super("Cash session not found: " + cashSessionId);
        this.cashSessionId = cashSessionId;
    }

    public UUID getCashSessionId() {
        return cashSessionId;
    }

}
