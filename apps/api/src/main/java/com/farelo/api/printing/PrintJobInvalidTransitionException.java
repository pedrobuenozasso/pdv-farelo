package com.farelo.api.printing;

import java.util.UUID;

/**
 * Thrown when an operation requires a {@link PrintJob} to be in a specific
 * status before transitioning to a new one, but its current status doesn't
 * match (FARELO-077: {@code PENDING}→{@code PRINTED}, {@code PENDING}→
 * {@code FAILED}). Same pattern as {@code
 * com.farelo.api.ordering.OrderInvalidTransitionException} — a single
 * reusable exception for both transitions, since both require the exact
 * same origin status ({@code PENDING}) and the message already names both
 * the attempted target and the required origin, so it reads correctly
 * regardless of which transition triggered it.
 */
public class PrintJobInvalidTransitionException extends RuntimeException {

    private final UUID printJobId;
    private final PrintJobStatus currentStatus;
    private final PrintJobStatus targetStatus;

    public PrintJobInvalidTransitionException(UUID printJobId, PrintJobStatus currentStatus, PrintJobStatus targetStatus) {
        super("Print job %s cannot transition to %s (current status: %s)"
                .formatted(printJobId, targetStatus, currentStatus));
        this.printJobId = printJobId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public UUID getPrintJobId() {
        return printJobId;
    }

    public PrintJobStatus getCurrentStatus() {
        return currentStatus;
    }

    public PrintJobStatus getTargetStatus() {
        return targetStatus;
    }

}
