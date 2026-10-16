package com.farelo.api.fiscal;

import java.util.Objects;
import java.util.UUID;

/** Data passed to a {@link FiscalProvider}; never a mutable JPA entity. */
public record FiscalAuthorizationRequest(UUID documentId, String accessKey, String signedXml) {

    public FiscalAuthorizationRequest {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(accessKey, "accessKey");
        Objects.requireNonNull(signedXml, "signedXml");
        if (!accessKey.matches("[0-9]{44}")) {
            throw new IllegalArgumentException("accessKey must have 44 digits");
        }
        if (signedXml.isBlank()) {
            throw new IllegalArgumentException("signedXml must not be blank");
        }
    }

    @Override
    public String toString() {
        return "FiscalAuthorizationRequest[documentId=" + documentId + ", fiscalPayload=REDACTED]";
    }
}
