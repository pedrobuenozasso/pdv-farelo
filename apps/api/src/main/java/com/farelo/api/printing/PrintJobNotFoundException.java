package com.farelo.api.printing;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link PrintJob} {@code id} that
 * does not exist. Same pattern as {@code
 * com.farelo.api.ordering.OrderNotFoundException} — keyed by the technical
 * {@code id} (UUID), the only way a {@code PrintJob} is ever looked up (it
 * has no business-facing sequential identifier, same as {@code Order}).
 */
public class PrintJobNotFoundException extends RuntimeException {

    private final UUID printJobId;

    public PrintJobNotFoundException(UUID printJobId) {
        super("Print job not found: " + printJobId);
        this.printJobId = printJobId;
    }

    public UUID getPrintJobId() {
        return printJobId;
    }

}
