package com.farelo.api.fiscal.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/v1/company-fiscal-configuration}. Never
 * expose the JPA entity directly on the API (see AGENTS.md) — this is the
 * boundary DTO.
 *
 * <p>A single request record for the single write operation this domain
 * has (there is no {@code POST}, see {@code
 * CompanyFiscalConfigurationController}'s javadoc) — unlike {@code
 * FiscalProfileRequest}/{@code FiscalProfileUpdateRequest}, there's no
 * create-vs-replace split to model since {@code PUT} is create-or-replace
 * either way.
 *
 * <p>{@code cnpj}/{@code legalName} are {@code @NotBlank} — the bare
 * minimum for this row to identify "which company" (see {@code
 * CompanyFiscalConfiguration}'s javadoc). No format/digit-check validation
 * on {@code cnpj} — prompt mestre names no validation rule for it, and
 * adding one (e.g. a 14-digit regex or a CNPJ check-digit algorithm) would
 * be inventing a rule with no textual basis, the same restraint already
 * applied to {@code FiscalProfile.name}. {@code tradeName}/{@code
 * stateRegistration}/{@code address} are plain nullable {@code String}s —
 * same optional shape as {@code FiscalProfileRequest.description} — and,
 * as a full replace, omitting one clears a previously-set value (same
 * "PUT is a full replace" convention as {@code FiscalProfileUpdateRequest}/
 * {@code ProductUpdateRequest}).
 */
public record CompanyFiscalConfigurationRequest(
        @NotBlank String cnpj,
        @NotBlank String legalName,
        String tradeName,
        String stateRegistration,
        String address) {
}
