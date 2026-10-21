package com.farelo.api.fiscal;

import java.util.Objects;

/** Original SOAP retained only for fiscal evidence, never for public errors. */
public record FiscalCancellationResponse(String rawXml) {
    public FiscalCancellationResponse {
        Objects.requireNonNull(rawXml, "rawXml");
    }

    @Override public String toString() { return "FiscalCancellationResponse[REDACTED]"; }
}
