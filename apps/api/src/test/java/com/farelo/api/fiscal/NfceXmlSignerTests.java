package com.farelo.api.fiscal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NfceXmlSignerTests {
    private static final NfceXmlSigner SIGNER = new NfceXmlSigner();
    private static KeyPair pair;
    private static X509Certificate certificate;

    @BeforeAll
    static void createTestCertificateInMemory() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        certificate = selfSignedTestCertificate(pair);
    }

    @Test
    void signsOnlyInfNfeAndProducesVerifiableXmlDsig() throws Exception {
        String signed = SIGNER.signHomologation(unsigned(), pair.getPrivate(), certificate);
        Document doc = parse(signed);
        Element inf = (Element) doc.getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "infNFe").item(0);
        Element signature = (Element) doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        assertEquals(doc.getDocumentElement(), signature.getParentNode());
        assertEquals(1, doc.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate").getLength());
        inf.setIdAttribute("Id", true);
        DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(), signature);
        // MOC 7.0 mandates SHA-1; JDK secureValidation disallows it for validation.
        // This test verifies our fixed, in-memory fixture only; never relax this for untrusted XML.
        context.setProperty("org.jcp.xml.dsig.secureValidation", false);
        XMLSignature xmlSignature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
        assertEquals("#" + inf.getAttribute("Id"), xmlSignature.getSignedInfo().getReferences().get(0).getURI());
        assertTrue(xmlSignature.validate(context));

        Document altered = parse(signed.replaceFirst("<vProd>12.50</vProd>", "<vProd>12.51</vProd>"));
        Element alteredInf = (Element) altered.getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "infNFe").item(0);
        alteredInf.setIdAttribute("Id", true);
        Element alteredSignature = (Element) altered.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext alteredContext = new DOMValidateContext(certificate.getPublicKey(), alteredSignature);
        alteredContext.setProperty("org.jcp.xml.dsig.secureValidation", false);
        assertFalse(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(alteredContext)
                .validate(alteredContext));
    }

    @Test
    void signedObservedFareloTaxBranchesValidateAgainstOfficial2026Schema() throws Exception {
        String signed = SIGNER.signHomologation(unsignedObservedTaxes(), pair.getPrivate(), certificate);
        String withQrCode = new NfceQrCodeV3().addToSignedHomologation(signed);
        Document document = parse(withQrCode);
        Element inf = (Element) document.getElementsByTagNameNS(
                "http://www.portalfiscal.inf.br/nfe", "infNFe").item(0);
        Element signature = (Element) document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        inf.setIdAttribute("Id", true);
        DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(), signature);
        context.setProperty("org.jcp.xml.dsig.secureValidation", false);
        assertTrue(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context));
        assertEquals("https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p="
                        + inf.getAttribute("Id").substring(3) + "|3|2",
                document.getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "qrCode")
                        .item(0).getTextContent());
        var resource = getClass().getResource("/nfe-xsd/PL_010f_v1.04/nfe_v4.00.xsd");
        assertTrue(resource != null);
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        var validator = factory.newSchema(resource).newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.validate(new StreamSource(new StringReader(withQrCode)));
    }

    @Test
    void qrCodeV3RefusesUnsignedProductionOrRepeatedDecoration() throws Exception {
        NfceQrCodeV3 qr = new NfceQrCodeV3();
        assertThrows(IllegalArgumentException.class, () -> qr.addToSignedHomologation(unsigned()));
        String signed = SIGNER.signHomologation(unsigned(), pair.getPrivate(), certificate);
        assertThrows(IllegalArgumentException.class,
                () -> qr.addToSignedHomologation(signed.replace("<tpAmb>2", "<tpAmb>1")));
        String decorated = qr.addToSignedHomologation(signed);
        assertThrows(IllegalArgumentException.class, () -> qr.addToSignedHomologation(decorated));
    }

    @Test
    void refusesMalformedOrWrongKeyXmlAndDoesNotSilentlyResign() throws Exception {
        String raw = unsigned();
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation(raw.replace("<infNFe", "<wrong"), pair.getPrivate(), certificate));
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation(raw.replace("<tpAmb>2", "<tpAmb>1"), pair.getPrivate(), certificate));
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation(raw.replace("<cNF>12345678", "<cNF>12345679"),
                        pair.getPrivate(), certificate));
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation("<!DOCTYPE NFe [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + raw,
                        pair.getPrivate(), certificate));
        String signed = SIGNER.signHomologation(raw, pair.getPrivate(), certificate);
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation(signed, pair.getPrivate(), certificate));
    }

    @Test
    void refusesKeyThatDoesNotMatchEmbeddedCertificate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation(unsigned(), other.getPrivate(), certificate));
    }

    private static String unsigned() {
        NfceXmlBuilder.Address address = new NfceXmlBuilder.Address("Rua Exemplo", "100", "Centro",
                "3550308", "Sao Paulo", "01001000");
        NfceXmlBuilder.Issuer issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", address);
        NfceXmlBuilder.Item item = new NfceXmlBuilder.Item("P1", "Cafe", "SEM GTIN", "19059090",
                "5102", "UN", new BigDecimal("1.0000"), new BigDecimal("12.5000"),
                new BigDecimal("12.50"), new NfceXmlBuilder.Taxes("0", "102", "07", "07"));
        return new NfceXmlBuilder().buildHomologation(new NfceXmlBuilder.Draft(issuer, "VENDA", 1,
                123, "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"), List.of(item),
                new BigDecimal("12.50"))).unsignedXml();
    }

    private static String unsignedObservedTaxes() {
        NfceXmlBuilder.Address address = new NfceXmlBuilder.Address("Rua Exemplo", "100", "Centro",
                "3550308", "Sao Paulo", "01001000");
        NfceXmlBuilder.Issuer issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", address);
        NfceXmlBuilder.OtherContribution zero = new NfceXmlBuilder.OtherContribution(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        NfceXmlBuilder.Taxes taxes = new NfceXmlBuilder.Taxes("0", "102", "99", "99", zero, zero);
        NfceXmlBuilder.Item item = new NfceXmlBuilder.Item("P1", "Cafe", "SEM GTIN", "19059090",
                "0300100", "5102", "UN", new BigDecimal("1.0000"), new BigDecimal("12.5000"),
                new BigDecimal("12.50"), taxes);
        return new NfceXmlBuilder().buildHomologation(new NfceXmlBuilder.Draft(issuer, "VENDA", 1,
                123, "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"), List.of(item),
                new BigDecimal("12.50"))).unsignedXml();
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Minimal X.509 v3 test certificate built and signed entirely in memory. */
    static X509Certificate selfSignedTestCertificate(KeyPair keyPair) throws Exception {
        byte[] sha256Rsa = seq(oid(1, 2, 840, 113549, 1, 1, 11), tag(0x05, new byte[0]));
        byte[] name = seq(tag(0x31, seq(oid(2, 5, 4, 3),
                tag(0x0C, "Farelo Test Only".getBytes(StandardCharsets.UTF_8)))));
        byte[] dates = seq(tag(0x17, "260101000000Z".getBytes(StandardCharsets.US_ASCII)),
                tag(0x17, "300101000000Z".getBytes(StandardCharsets.US_ASCII)));
        byte[] tbs = seq(tag(0xA0, tag(0x02, BigInteger.valueOf(2).toByteArray())),
                tag(0x02, BigInteger.ONE.toByteArray()), sha256Rsa, name, dates, name,
                keyPair.getPublic().getEncoded());
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(tbs);
        byte[] certificateDer = seq(tbs, sha256Rsa, bitString(signer.sign()));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificateDer));
    }

    private static byte[] oid(int... parts) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        bytes.write(parts[0] * 40 + parts[1]);
        for (int i = 2; i < parts.length; i++) {
            int value = parts[i];
            byte[] buffer = new byte[5];
            int at = buffer.length;
            buffer[--at] = (byte) (value & 0x7F);
            value >>>= 7;
            while (value > 0) {
                buffer[--at] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            bytes.write(buffer, at, buffer.length - at);
        }
        return tag(0x06, bytes.toByteArray());
    }

    private static byte[] bitString(byte[] value) {
        return tag(0x03, concat(new byte[]{0}, value));
    }

    private static byte[] seq(byte[]... values) {
        return tag(0x30, concat(values));
    }

    private static byte[] tag(int tag, byte[] body) {
        byte[] length = body.length < 128 ? new byte[]{(byte) body.length}
                : body.length < 256 ? new byte[]{(byte) 0x81, (byte) body.length}
                : new byte[]{(byte) 0x82, (byte) (body.length >>> 8), (byte) body.length};
        return concat(new byte[]{(byte) tag}, length, body);
    }

    private static byte[] concat(byte[]... arrays) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] bytes : arrays) out.writeBytes(bytes);
        return out.toByteArray();
    }
}
