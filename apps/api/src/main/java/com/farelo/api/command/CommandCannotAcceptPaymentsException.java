package com.farelo.api.command;

/**
 * Thrown when an operation requires a {@link Command} to be able to accept a
 * payment ({@link CommandStatus#OPEN} or {@link
 * CommandStatus#PAYMENT_REQUESTED}) but it currently isn't ({@code
 * AVAILABLE}, {@code CLOSED} or {@code BLOCKED}). See {@link
 * CommandService#findForPayment(int)} (FARELO-141).
 *
 * <p>Same family as {@link CommandCannotAcceptOrdersException} (thrown by
 * {@link CommandService#openForOrdering(int)}) and {@link
 * CommandCannotBeClosedException} (thrown by {@link CommandService#close(int)})
 * — a dedicated exception per "what does this command need to be able to do"
 * question, rather than reusing one of those two or {@link
 * CommandNotAvailableException}: none of their messages would read correctly
 * here. {@code CommandNotAvailableException} implies {@code AVAILABLE} is the
 * one valid origin state, which is backwards for payments (a command that is
 * still {@code AVAILABLE} — never opened — is exactly the common case this
 * exception needs to reject, and it emphatically *is* "available"; that's why
 * nothing has been ordered or paid for yet). {@code
 * CommandCannotAcceptOrdersException} accepts {@code AVAILABLE} as a valid
 * origin (placing the first order opens the command as a side effect) and
 * rejects {@code OPEN} for a different reason set — the opposite of what a
 * payment needs.
 */
public class CommandCannotAcceptPaymentsException extends RuntimeException {

    private final int number;
    private final CommandStatus currentStatus;

    public CommandCannotAcceptPaymentsException(int number, CommandStatus currentStatus) {
        super("Command %d cannot accept payments (current status: %s, expected OPEN or PAYMENT_REQUESTED)"
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
