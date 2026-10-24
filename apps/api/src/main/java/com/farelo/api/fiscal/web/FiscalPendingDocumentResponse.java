package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalDocument;
import com.farelo.api.fiscal.FiscalDocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Deliberately excludes XML and fiscal identifiers from this preparation command. */
public record FiscalPendingDocumentResponse(UUID id, int commandNumber,
                                            FiscalDocumentStatus status, OffsetDateTime createdAt) {
    public static FiscalPendingDocumentResponse from(FiscalDocument document) {
        return new FiscalPendingDocumentResponse(document.getId(),
                document.getCommand().getNumber(), document.getStatus(), document.getCreatedAt());
    }
}
