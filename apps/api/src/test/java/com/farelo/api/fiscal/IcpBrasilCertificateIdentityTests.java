package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IcpBrasilCertificateIdentityTests {
    @Test
    void readsCnpjFromParsedCertificateOtherNameAndRejectsDifferentIssuer() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var certificate = TestIcpBrasilCertificate.issue(generator.generateKeyPair(), "11222333000181");
        assertEquals("11222333000181", IcpBrasilCertificateIdentity.cnpjOtherName(
                (byte[]) certificate.getSubjectAlternativeNames().iterator().next().get(1)));
        IcpBrasilCertificateIdentity.requireIssuerCnpj(certificate, "11222333000181");
        assertThrows(IllegalArgumentException.class,
                () -> IcpBrasilCertificateIdentity.requireIssuerCnpj(certificate, "11222333000182"));
        var octet = TestIcpBrasilCertificate.issue(generator.generateKeyPair(), "11222333000181", 0x04);
        IcpBrasilCertificateIdentity.requireIssuerCnpj(octet, "11222333000181");
        var unsupported = TestIcpBrasilCertificate.issue(generator.generateKeyPair(), "11222333000181", 0x16);
        assertThrows(IllegalArgumentException.class,
                () -> IcpBrasilCertificateIdentity.requireIssuerCnpj(unsupported, "11222333000181"));
    }

    @Test
    void absentOrMalformedIdentityFailsClosed() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var absent = NfceXmlSignerTests.selfSignedTestCertificate(generator.generateKeyPair());
        assertThrows(IllegalArgumentException.class,
                () -> IcpBrasilCertificateIdentity.requireIssuerCnpj(absent, "11222333000181"));
        assertThrows(IllegalArgumentException.class,
                () -> IcpBrasilCertificateIdentity.cnpjOtherName(new byte[]{0x30, 0x00}));
    }
}
