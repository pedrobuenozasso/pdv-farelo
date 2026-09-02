package com.farelo.api.ordering;

/**
 * Lifecycle states of an {@link Order}. See prompt mestre seções 6, 9 e 31
 * for the full flow — transitions between these states are out of scope
 * for this ticket (FARELO-050) and land in FARELO-053+.
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PREPARING,
    READY,
    DELIVERED,
    CANCELLED
}
