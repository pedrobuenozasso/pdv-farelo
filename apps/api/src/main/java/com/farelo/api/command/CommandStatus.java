package com.farelo.api.command;

/**
 * Lifecycle states of a {@link Command} (comanda). See prompt mestre seções
 * 7-8 for the full state machine — transitions between these states are out
 * of scope for this ticket (FARELO-030) and land in FARELO-032+.
 */
public enum CommandStatus {
    AVAILABLE,
    OPEN,
    PAYMENT_REQUESTED,
    CLOSED,
    BLOCKED
}
