package com.farelo.api.fiscal;

import java.util.Objects;

/** Raw reply to a read-only query by access key. */
public record FiscalConsultationResponse(String rawXml) {
    public FiscalConsultationResponse {
        Objects.requireNonNull(rawXml, "rawXml");
        if (rawXml.isBlank()) throw new IllegalArgumentException("empty SEFAZ consultation reply");
    }

    @Override public String toString() { return "FiscalConsultationResponse[rawXml=REDACTED]"; }
}
