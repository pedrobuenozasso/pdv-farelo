package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfceIssuerMapperTests {
    @Test
    void requiresStructuredFieldsAndNeverParsesLegacyAddress() {
        var company = new CompanyFiscalConfiguration("12345678000195", "Farelo");
        company.setAddress("Rua legada, 12, São Paulo");

        assertThatThrownBy(() -> NfceIssuerMapper.map(company))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state registration");

        company.setStateRegistration("123456789012");
        company.setTaxRegime("1");
        assertThatThrownBy(() -> NfceIssuerMapper.map(company))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("street");
    }

    @Test
    void mapsOnlyExplicitIssuerValues() {
        var company = new CompanyFiscalConfiguration("12345678000195", "Farelo");
        company.setStateRegistration("123456789012");
        company.setTaxRegime("1");
        company.setStreet("Rua das Flores");
        company.setStreetNumber("12");
        company.setDistrict("Centro");
        company.setCityCode("3550308");
        company.setCity("São Paulo");
        company.setPostalCode("01001000");

        NfceXmlBuilder.Issuer issuer = NfceIssuerMapper.map(company);

        assertThat(issuer.cnpj()).isEqualTo("12345678000195");
        assertThat(issuer.crt()).isEqualTo("1");
        assertThat(issuer.address().cityCode()).isEqualTo("3550308");
        assertThat(issuer.address().street()).isEqualTo("Rua das Flores");
    }
}
