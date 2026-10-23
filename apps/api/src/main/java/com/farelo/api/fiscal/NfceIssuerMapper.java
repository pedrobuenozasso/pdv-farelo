package com.farelo.api.fiscal;

import java.util.Objects;

/** Converts reviewed company registration fields to the explicit NFC-e issuer. */
public final class NfceIssuerMapper {
    private NfceIssuerMapper() { }

    public static NfceXmlBuilder.Issuer map(CompanyFiscalConfiguration company) {
        Objects.requireNonNull(company, "company fiscal configuration");
        String cnpj = required(company.getCnpj(), "CNPJ");
        String legalName = required(company.getLegalName(), "legal name");
        String registration = required(company.getStateRegistration(), "state registration");
        String regime = required(company.getTaxRegime(), "tax regime");
        if (!regime.equals("1") && !regime.equals("3")) {
            throw new IllegalArgumentException("unsupported tax regime");
        }
        NfceXmlBuilder.Address address = new NfceXmlBuilder.Address(
                required(company.getStreet(), "street"),
                required(company.getStreetNumber(), "street number"),
                required(company.getDistrict(), "district"),
                required(company.getCityCode(), "city code"),
                required(company.getCity(), "city"),
                required(company.getPostalCode(), "postal code"));
        // The older free-text address is intentionally never parsed into
        // fiscal fields. NfceXmlBuilder validates their exact XML formats.
        return new NfceXmlBuilder.Issuer(cnpj, legalName, registration, regime, address);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing issuer " + label);
        }
        return value;
    }
}
