package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalDocument;
import com.farelo.api.fiscal.FiscalDocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing every public field of {@link FiscalDocument} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code commandNumber}, not the command's UUID {@code id} — same
 * identifier convention {@code PaymentResponse}/{@code OrderResponse}
 * already follow. {@code documentNumber}/{@code series}/{@code accessKey}/
 * {@code protocolNumber}/{@code xmlContent}/{@code authorizedAt} are all
 * {@code null} for every document today — nothing in this codebase
 * populates them yet (Epic 12, explicitly out of scope here; see {@link
 * FiscalDocument}'s javadoc) — but are surfaced now so this response shape
 * doesn't need to change again once a real emission producer exists.
 */
public record FiscalDocumentResponse(
        UUID id,
        int commandNumber,
        FiscalDocumentStatus status,
        Integer documentNumber,
        Integer series,
        String accessKey,
        String protocolNumber,
        String xmlContent,
        OffsetDateTime authorizedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static FiscalDocumentResponse from(FiscalDocument fiscalDocument) {
        return new FiscalDocumentResponse(
                fiscalDocument.getId(),
                fiscalDocument.getCommand().getNumber(),
                fiscalDocument.getStatus(),
                fiscalDocument.getDocumentNumber(),
                fiscalDocument.getSeries(),
                fiscalDocument.getAccessKey(),
                fiscalDocument.getProtocolNumber(),
                fiscalDocument.getXmlContent(),
                fiscalDocument.getAuthorizedAt(),
                fiscalDocument.getCreatedAt(),
                fiscalDocument.getUpdatedAt());
    }

}
