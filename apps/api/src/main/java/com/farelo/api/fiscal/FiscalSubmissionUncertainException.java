package com.farelo.api.fiscal;

/** A submission may have reached SEFAZ; consult the access key before retrying. */
public final class FiscalSubmissionUncertainException extends RuntimeException {
    public FiscalSubmissionUncertainException() {
        super("NFC-e submission outcome is uncertain; consult access key before retrying");
    }
}
