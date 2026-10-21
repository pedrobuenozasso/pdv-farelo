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
import java.io.StringReader;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NfceCancellationEventSignerTests {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final NfceCancellationEventSigner SIGNER = new NfceCancellationEventSigner();
    private static KeyPair pair;
    private static X509Certificate certificate;

    @BeforeAll
    static void fixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
    }

    @Test
    void buildsOrdinaryHomologationCancellationAndVerifiableSignature() throws Exception {
        String xml = SIGNER.signHomologation(draft(), pair.getPrivate(), certificate);
        Document document = parse(xml);
        Element batch = document.getDocumentElement();
        assertEquals("envEvento", batch.getLocalName());
        assertEquals("1.00", batch.getAttribute("versao"));
        assertEquals("7", text(document, "idLote"));
        assertEquals("35", text(document, "cOrgao"));
        assertEquals("2", text(document, "tpAmb"));
        assertEquals("110111", text(document, "tpEvento"));
        assertEquals("1", text(document, "nSeqEvento"));
        assertEquals("Cancelamento", text(document, "descEvento"));
        assertEquals("13526000000000001", text(document, "nProt"));
        assertEquals("Venda desfeita antes da entrega", text(document, "xJust"));
        assertEquals("2026-10-21T12:30:00-03:00", text(document, "dhEvento"));

        Element info = (Element) document.getElementsByTagNameNS(NFE_NS, "infEvento").item(0);
        assertEquals("ID110111" + draft().accessKey() + "01", info.getAttribute("Id"));
        Element signature = (Element) document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        assertEquals(info.getParentNode(), signature.getParentNode());
        assertTrue(validSignature(document));
        assertFalse(validSignature(parse(xml.replace("Venda desfeita antes da entrega", "Venda desfeita apos a entrega"))));
    }

    @Test
    void rejectsWrongIssuerWrongModelShortReasonAndMismatchedCertificate() throws Exception {
        var original = draft();
        assertThrows(IllegalArgumentException.class, () -> SIGNER.signHomologation(
                new NfceCancellationEventSigner.Draft(original.accessKey(), "00000000000000",
                        original.authorizationProtocol(), original.justification(), original.eventTime(), 7),
                pair.getPrivate(), certificate));
        assertThrows(IllegalArgumentException.class, () -> SIGNER.signHomologation(
                new NfceCancellationEventSigner.Draft(original.accessKey().replaceFirst("65", "55"),
                        original.issuerCnpj(), original.authorizationProtocol(), original.justification(),
                        original.eventTime(), 7), pair.getPrivate(), certificate));
        assertThrows(IllegalArgumentException.class, () -> SIGNER.signHomologation(
                new NfceCancellationEventSigner.Draft(original.accessKey(), original.issuerCnpj(),
                        original.authorizationProtocol(), "Curta", original.eventTime(), 7),
                pair.getPrivate(), certificate));
        KeyPair another = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        assertThrows(IllegalArgumentException.class,
                () -> SIGNER.signHomologation(original, another.getPrivate(), certificate));
    }

    private static NfceCancellationEventSigner.Draft draft() {
        NfceXmlBuilder.Address address = new NfceXmlBuilder.Address("Rua Exemplo", "100", "Centro",
                "3550308", "Sao Paulo", "01001000");
        NfceXmlBuilder.Issuer issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", address);
        NfceXmlBuilder.Item item = new NfceXmlBuilder.Item("P1", "Cafe", "SEM GTIN", "19059090",
                "5102", "UN", new BigDecimal("1.0000"), new BigDecimal("12.5000"),
                new BigDecimal("12.50"), new NfceXmlBuilder.Taxes("0", "102", "07", "07"));
        String key = new NfceXmlBuilder().buildHomologation(new NfceXmlBuilder.Draft(issuer, "VENDA",
                1, 123, "12345678", OffsetDateTime.parse("2026-10-21T11:00:00-03:00"),
                List.of(item), new BigDecimal("12.50"))).accessKey();
        return new NfceCancellationEventSigner.Draft(key, issuer.cnpj(), "13526000000000001",
                "Venda desfeita antes da entrega", OffsetDateTime.parse("2026-10-21T12:30:00-03:00"), 7);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String text(Document document, String tag) {
        return document.getElementsByTagNameNS(NFE_NS, tag).item(0).getTextContent();
    }

    private static boolean validSignature(Document document) throws Exception {
        Element info = (Element) document.getElementsByTagNameNS(NFE_NS, "infEvento").item(0);
        info.setIdAttribute("Id", true);
        Element signature = (Element) document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(), signature);
        // MOC 7.0 prescribes SHA-1. This relaxation is only for a trusted in-memory fixture.
        context.setProperty("org.jcp.xml.dsig.secureValidation", false);
        return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context);
    }
}
