package com.farelo.api.fiscal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Persists a definite per-note SEFAZ rejection; no cashier flow calls it yet. */
@Service
public class FiscalRejectionRecorder {
    private final FiscalDocumentRepository documents;

    public FiscalRejectionRecorder(FiscalDocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional
    public FiscalDocument record(FiscalAuthorizationRequest request, FiscalAuthorizationResponse response) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        var rejection = SefazSpRejectionParser.parse(request, response);
        FiscalDocument document = documents.findByIdForUpdate(request.documentId())
                .orElseThrow(() -> new FiscalDocumentNotFoundException(request.documentId()));
        if (!request.accessKey().equals(document.getAccessKey())
                || document.getStatus() != FiscalDocumentStatus.PROCESSING
                    && document.getStatus() != FiscalDocumentStatus.CONTINGENCY) {
            throw new IllegalStateException("fiscal document is not ready for rejection");
        }
        document.setXmlContent(rejection.submittedXml());
        document.setRejectionCode(rejection.code());
        document.setRejectionReason(rejection.reason());
        document.setRejectionResponseXml(rejection.responseXml());
        document.setStatus(FiscalDocumentStatus.REJECTED);
        return documents.save(document);
    }
}
