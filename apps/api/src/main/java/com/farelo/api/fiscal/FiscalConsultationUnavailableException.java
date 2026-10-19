package com.farelo.api.fiscal;

/** A failed consultation leaves the NFC-e outcome unresolved. */
public final class FiscalConsultationUnavailableException extends RuntimeException {
    public FiscalConsultationUnavailableException() {
        super("NFC-e consultation failed; keep the document unresolved and do not resubmit");
    }
}
