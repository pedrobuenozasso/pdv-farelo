package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.CompanyFiscalConfiguration;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link
 * CompanyFiscalConfiguration} — the JPA entity itself is never returned by
 * the API (see AGENTS.md).
 *
 * <p>{@code id} is included for consistency with every other response DTO
 * in this codebase, even though the API never accepts it back from a
 * client (no {@code {id}} path variable anywhere on this domain's
 * endpoints — see {@code CompanyFiscalConfigurationController}'s javadoc).
 *
 * The optional structured issuer fields remain null until explicitly reviewed.
 */
public record CompanyFiscalConfigurationResponse(
        UUID id,
        String cnpj,
        String legalName,
        String tradeName,
        String stateRegistration,
        String address,
        String taxRegime,
        String street,
        String streetNumber,
        String district,
        String cityCode,
        String city,
        String postalCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CompanyFiscalConfigurationResponse from(CompanyFiscalConfiguration companyFiscalConfiguration) {
        return new CompanyFiscalConfigurationResponse(
                companyFiscalConfiguration.getId(),
                companyFiscalConfiguration.getCnpj(),
                companyFiscalConfiguration.getLegalName(),
                companyFiscalConfiguration.getTradeName(),
                companyFiscalConfiguration.getStateRegistration(),
                companyFiscalConfiguration.getAddress(),
                companyFiscalConfiguration.getTaxRegime(),
                companyFiscalConfiguration.getStreet(),
                companyFiscalConfiguration.getStreetNumber(),
                companyFiscalConfiguration.getDistrict(),
                companyFiscalConfiguration.getCityCode(),
                companyFiscalConfiguration.getCity(),
                companyFiscalConfiguration.getPostalCode(),
                companyFiscalConfiguration.getCreatedAt(),
                companyFiscalConfiguration.getUpdatedAt());
    }

}
