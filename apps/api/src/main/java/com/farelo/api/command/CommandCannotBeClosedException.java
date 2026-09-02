package com.farelo.api.command;

/**
 * Thrown when an operation requires a {@link Command} to be in a closable
 * state ({@link CommandStatus#OPEN} or
 * {@link CommandStatus#PAYMENT_REQUESTED}) but it currently isn't.
 *
 * <p>Deliberately a separate exception from {@link CommandNotAvailableException}
 * rather than reused: that one's message ("not available") only reads
 * correctly relative to {@code AVAILABLE} being the single valid origin
 * state, as it is for {@code open}. Reusing it here would read backwards
 * for the most common invalid-close case — a command still {@code
 * AVAILABLE} (never opened) — since that command *is* available; that's
 * exactly why it can't be closed. A close-specific exception keeps the
 * message accurate for every invalid origin state.
 */
public class CommandCannotBeClosedException extends RuntimeException {

    private final int number;
    private final CommandStatus currentStatus;

    public CommandCannotBeClosedException(int number, CommandStatus currentStatus) {
        super("Command %d cannot be closed (current status: %s, expected OPEN or PAYMENT_REQUESTED)"
                .formatted(number, currentStatus));
        this.number = number;
        this.currentStatus = currentStatus;
    }

    public int getNumber() {
        return number;
    }

    public CommandStatus getCurrentStatus() {
        return currentStatus;
    }

}
