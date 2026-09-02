package com.farelo.api.command;

/**
 * Thrown when an operation requires a {@link Command} to be able to accept
 * a new order ({@link CommandStatus#AVAILABLE} or {@link
 * CommandStatus#OPEN}) but it currently isn't ({@code PAYMENT_REQUESTED},
 * {@code CLOSED} or {@code BLOCKED}). See {@link
 * CommandService#openForOrdering(int)}.
 */
public class CommandCannotAcceptOrdersException extends RuntimeException {

    private final int number;
    private final CommandStatus currentStatus;

    public CommandCannotAcceptOrdersException(int number, CommandStatus currentStatus) {
        super("Command %d cannot accept orders (current status: %s)".formatted(number, currentStatus));
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
