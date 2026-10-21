package com.farelo.api.fiscal;

import java.util.Objects;
import java.util.UUID;

/** One signed cancellation event, never a mutable fiscal entity or credential. */
public record FiscalCancellationRequest(UUID documentId, String accessKey, String signedEventXml) {
    public FiscalCancellationRequest {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(accessKey, "accessKey");
        Objects.requireNonNull(signedEventXml, "signedEventXml");
        if (!accessKey.matches("[0-9]{44}") || signedEventXml.isBlank()) {
            throw new IllegalArgumentException("invalid cancellation request");
        }
    }

    @Override public String toString() {
        return "FiscalCancellationRequest[documentId=" + documentId + ", fiscalPayload=REDACTED]";
    }
}
