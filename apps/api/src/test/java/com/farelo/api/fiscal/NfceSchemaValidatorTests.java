package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfceSchemaValidatorTests {
    @TempDir Path temp;

    @Test
    void validatesUsingAnExplicitLocalSchema() throws Exception {
        NfceSchemaValidator validator = new NfceSchemaValidator(schema());

        assertThatCode(() -> validator.validate("<NFe xmlns='http://www.portalfiscal.inf.br/nfe' Id='NFe123'/>"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("<NFe xmlns='http://www.portalfiscal.inf.br/nfe'/>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NFC-e XSD validation failed");
    }

    @Test
    void rejectsDtdAndMissingSchema() throws Exception {
        NfceSchemaValidator validator = new NfceSchemaValidator(schema());

        assertThatThrownBy(() -> validator.validate("<!DOCTYPE NFe [<!ENTITY x 'secret'>]><NFe/>") )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DTD");
        assertThatThrownBy(() -> new NfceSchemaValidator(temp.resolve("absent.xsd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NFC-e XSD is unavailable");
    }

    private Path schema() throws Exception {
        Path file = temp.resolve("nfe-test.xsd");
        Files.writeString(file, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://www.portalfiscal.inf.br/nfe"
                           xmlns="http://www.portalfiscal.inf.br/nfe"
                           elementFormDefault="qualified">
                  <xs:element name="NFe">
                    <xs:complexType><xs:attribute name="Id" type="xs:string" use="required"/></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        return file;
    }
}
