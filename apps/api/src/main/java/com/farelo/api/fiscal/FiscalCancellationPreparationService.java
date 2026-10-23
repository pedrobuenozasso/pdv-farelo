package com.farelo.api.fiscal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Prepares an ordinary SP homologation cancellation from a persisted authorization.
 * No transport or database mutation occurs here. A future submission workflow must
 * persist an attempt before sending, and reconcile uncertain responses by key/event
 * before allowing another send.
 */
@Service
public class FiscalCancellationPreparationService {
    private final FiscalDocumentRepository documents;
    private final NfceCancellationEventSigner signer;
    private final Clock clock;

    @Autowired
    public FiscalCancellationPreparationService(FiscalDocumentRepository documents) {
        this(documents, new NfceCancellationEventSigner(), Clock.systemUTC());
    }

    FiscalCancellationPreparationService(FiscalDocumentRepository documents,
                                         NfceCancellationEventSigner signer, Clock clock) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public FiscalCancellationRequest prepare(UUID documentId, String justification,
                                             boolean merchandiseLeft,
                                             Pkcs12SigningCredentials credentials) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(credentials, "credentials");
        FiscalDocument document = documents.findById(documentId)
                .orElseThrow(() -> new FiscalDocumentNotFoundException(documentId));
        if (document.getStatus() != FiscalDocumentStatus.AUTHORIZED
                || document.getCancellationProtocolNumber() != null
                || document.getXmlContent() == null || document.getXmlContent().isBlank()
                || document.getAccessKey() == null || document.getProtocolNumber() == null
                || document.getAuthorizedAt() == null) {
            throw new IllegalStateException("fiscal document is not ready for cancellation");
        }
        String accessKey = document.getAccessKey();
        if (!accessKey.matches("35[0-9]{18}65[0-9]{22}")) {
            throw new IllegalStateException("only authorized SP NFC-e can be cancelled here");
        }
        OffsetDateTime eventTime = OffsetDateTime.now(clock);
        long batchId = Math.floorMod(clock.millis(), 999_999_999_999_999L) + 1;
        var draft = new NfceCancellationEventSigner.Draft(
                accessKey, accessKey.substring(6, 20), document.getProtocolNumber(),
                justification, eventTime, document.getAuthorizedAt(), merchandiseLeft, batchId);
        String signedXml = signer.signHomologation(
                draft, credentials.privateKey(), credentials.certificate());
        return new FiscalCancellationRequest(documentId, accessKey, signedXml);
    }
}
