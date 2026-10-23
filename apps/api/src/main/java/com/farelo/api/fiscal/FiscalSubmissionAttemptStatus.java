package com.farelo.api.fiscal;

/** No state here authorizes an automatic repeat of a transmitted NFC-e. */
public enum FiscalSubmissionAttemptStatus {
    RESERVED, DISPATCHING, UNCERTAIN, RESOLVED
}
