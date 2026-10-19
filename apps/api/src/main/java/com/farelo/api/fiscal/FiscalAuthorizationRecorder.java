package com.farelo.api.fiscal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Persists a confirmed SEFAZ authorization; no endpoint or sender calls it yet. */
@Service
public class FiscalAuthorizationRecorder {
    private final FiscalDocumentRepository documents;

    public FiscalAuthorizationRecorder(FiscalDocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional
    public FiscalDocument record(FiscalAuthorizationRequest request, FiscalAuthorizationResponse response) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        // Parsing happens before the row lock and before any mutation. A
        // rejection or ambiguous SOAP reply must never become AUTHORIZED.
        var authorization = SefazSpAuthorizationParser.parse(request, response);
        FiscalDocument document = documents.findByIdForUpdate(request.documentId())
                .orElseThrow(() -> new FiscalDocumentNotFoundException(request.documentId()));
        if (!request.accessKey().equals(document.getAccessKey())
                || document.getStatus() != FiscalDocumentStatus.PROCESSING
                    && document.getStatus() != FiscalDocumentStatus.CONTINGENCY) {
            throw new IllegalStateException("fiscal document is not ready for authorization");
        }
        document.setProtocolNumber(authorization.protocolNumber());
        document.setAuthorizedAt(authorization.authorizedAt());
        document.setXmlContent(authorization.processedXml());
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        return documents.save(document);
    }
}
