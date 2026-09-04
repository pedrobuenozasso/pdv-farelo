package com.farelo.api.fiscal;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link FiscalDocument} {@code id}
 * that does not exist — either genuinely absent, or (per {@link
 * FiscalDocumentService#transition(int, UUID, FiscalDocumentStatus)}'s
 * javadoc) present under a <em>different</em> comanda than the one named in
 * the URL, deliberately reported the same way rather than as a distinct
 * "wrong command" error. Same pattern as {@code
 * com.farelo.api.printing.PrintJobNotFoundException}/{@code
 * com.farelo.api.ordering.OrderNotFoundException} — keyed by the technical
 * {@code id} (UUID), the only way a {@code FiscalDocument} is ever looked up
 * on its own (it has no business-facing sequential identifier; it is only
 * ever listed scoped to a comanda's {@code number} — see {@link
 * FiscalDocumentRepository#findByCommandOrderByCreatedAtAsc}).
 */
public class FiscalDocumentNotFoundException extends RuntimeException {

    private final UUID fiscalDocumentId;

    public FiscalDocumentNotFoundException(UUID fiscalDocumentId) {
        super("Fiscal document not found: " + fiscalDocumentId);
        this.fiscalDocumentId = fiscalDocumentId;
    }

    public UUID getFiscalDocumentId() {
        return fiscalDocumentId;
    }

}
