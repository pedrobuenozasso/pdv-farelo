package com.farelo.api.command;

/**
 * Thrown when an operation requires a {@link Command} to be
 * {@link CommandStatus#AVAILABLE} but it currently isn't (e.g. opening a
 * command that is already {@code OPEN}). {@code COMMAND_NOT_AVAILABLE} is
 * the exact error {@code code} used as the example in AGENTS.md/docs/api.md
 * for the standard error format — a signal that this is precisely the kind
 * of business error that format was designed for.
 */
public class CommandNotAvailableException extends RuntimeException {

    private final int number;
    private final CommandStatus currentStatus;

    public CommandNotAvailableException(int number, CommandStatus currentStatus) {
        super("Command %d is not available (current status: %s)".formatted(number, currentStatus));
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
