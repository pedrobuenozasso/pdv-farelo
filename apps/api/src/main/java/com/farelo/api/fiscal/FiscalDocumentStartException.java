package com.farelo.api.fiscal;

/** A closed visit cannot safely acquire a new pending fiscal placeholder. */
public class FiscalDocumentStartException extends IllegalStateException {
    private final String code;

    public FiscalDocumentStartException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
