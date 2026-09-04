package com.farelo.api.fiscal;

/**
 * Thrown by {@code GET /api/v1/company-fiscal-configuration} when no
 * {@link CompanyFiscalConfiguration} row has been set yet (i.e. {@code PUT}
 * was never called) — same "not found" shape as {@link
 * FiscalProfileNotFoundException} and every other {@code *NotFoundException}
 * in this codebase, but with no id parameter: there is nothing to identify,
 * only "configured" vs. "not configured yet".
 */
public class CompanyFiscalConfigurationNotFoundException extends RuntimeException {

    public CompanyFiscalConfigurationNotFoundException() {
        super("Company fiscal configuration has not been set yet");
    }

}
