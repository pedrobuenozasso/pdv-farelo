package com.farelo.api.fiscal;

/** Even NOT_FOUND requires operational review; it never authorizes an automatic resend. */
public enum FiscalConsultationResult {
    AUTHORIZED,
    NOT_FOUND,
    UNRESOLVED
}
