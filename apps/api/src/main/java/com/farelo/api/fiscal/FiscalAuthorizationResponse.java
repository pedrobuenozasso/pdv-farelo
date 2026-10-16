package com.farelo.api.fiscal;

import java.util.Objects;

/** Raw authorizer reply; later tickets parse and persist the fiscal outcome. */
public record FiscalAuthorizationResponse(String rawXml) {

    public FiscalAuthorizationResponse {
        Objects.requireNonNull(rawXml, "rawXml");
        if (rawXml.isBlank()) {
            throw new IllegalArgumentException("rawXml must not be blank");
        }
    }

    @Override
    public String toString() {
        return "FiscalAuthorizationResponse[rawXml=REDACTED]";
    }
}
