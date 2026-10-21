package com.farelo.api.fiscal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Persists a linked SEFAZ cancellation without replacing the authorized XML. */
@Service
public class FiscalCancellationRecorder {
    private final FiscalDocumentRepository documents;

    public FiscalCancellationRecorder(FiscalDocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional
    public FiscalDocument record(FiscalCancellationRequest request, FiscalCancellationResponse response) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        var event = SefazSpCancellationParser.parse(request, response);
        FiscalDocument document = documents.findByIdForUpdate(request.documentId())
                .orElseThrow(() -> new FiscalDocumentNotFoundException(request.documentId()));
        if (document.getStatus() != FiscalDocumentStatus.AUTHORIZED
                || !request.accessKey().equals(document.getAccessKey())
                || !event.originalProtocol().equals(document.getProtocolNumber())
                || document.getXmlContent() == null || document.getXmlContent().isBlank()) {
            throw new IllegalStateException("fiscal document is not ready for cancellation");
        }
        document.setCancellationProtocolNumber(event.eventProtocol());
        document.setCancelledAt(event.cancelledAt());
        document.setCancellationEventXml(event.processedEventXml());
        document.setCancellationResponseXml(response.rawXml());
        document.setStatus(FiscalDocumentStatus.CANCELLED);
        return documents.save(document);
    }
}
