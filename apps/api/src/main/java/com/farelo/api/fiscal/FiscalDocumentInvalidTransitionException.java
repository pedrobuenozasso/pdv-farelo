package com.farelo.api.fiscal;

import java.util.UUID;

/**
 * Thrown when {@link FiscalDocumentService#transition(UUID,
 * FiscalDocumentStatus)} (FARELO-157) is asked to move a {@link
 * FiscalDocument} to a {@link FiscalDocumentStatus} its current status does
 * not legally allow — see that method's javadoc, and {@code
 * FiscalDocumentService#LEGAL_TRANSITIONS}, for the full transition table
 * and the domain reasoning behind every edge.
 *
 * <p>Same pattern/shape as {@code
 * com.farelo.api.printing.PrintJobInvalidTransitionException}/{@code
 * com.farelo.api.ordering.OrderInvalidTransitionException}: a single
 * reusable exception for every illegal move, not one subclass per
 * forbidden edge — the message already names both the attempted target and
 * the required/actual origin, so it reads correctly regardless of which of
 * the (many) illegal edges triggered it, and {@code FiscalDocumentStatus}
 * has six values (up to 30 ordered pairs) where per-edge subclassing would
 * not scale the way it arguably wouldn't even for the two/three-transition
 * domains that already rejected it.
 */
public class FiscalDocumentInvalidTransitionException extends RuntimeException {

    private final UUID fiscalDocumentId;
    private final FiscalDocumentStatus currentStatus;
    private final FiscalDocumentStatus targetStatus;

    public FiscalDocumentInvalidTransitionException(
            UUID fiscalDocumentId, FiscalDocumentStatus currentStatus, FiscalDocumentStatus targetStatus) {
        super("Fiscal document %s cannot transition to %s (current status: %s)"
                .formatted(fiscalDocumentId, targetStatus, currentStatus));
        this.fiscalDocumentId = fiscalDocumentId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public UUID getFiscalDocumentId() {
        return fiscalDocumentId;
    }

    public FiscalDocumentStatus getCurrentStatus() {
        return currentStatus;
    }

    public FiscalDocumentStatus getTargetStatus() {
        return targetStatus;
    }

}
