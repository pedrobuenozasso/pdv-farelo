package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SefazSpNfceProviderTests {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";

    @Test
    void publishedHomologationWsdlMatchesEndpointAndSoapAction() throws Exception {
        String wsdl = "http://schemas.xmlsoap.org/wsdl/";
        String soap12 = "http://schemas.xmlsoap.org/wsdl/soap12/";
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document contract;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/sefaz-sp-nfce-autorizacao4-homologacao.wsdl"))) {
            contract = factory.newDocumentBuilder().parse(input);
        }
        var address = (org.w3c.dom.Element) contract.getElementsByTagNameNS(soap12, "address").item(0);
        assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeAutorizacao4.asmx",
                address.getAttribute("location"));
        var operations = contract.getElementsByTagNameNS(wsdl, "operation");
        boolean matched = false;
        for (int i = 0; i < operations.getLength(); i++) {
            var operation = (org.w3c.dom.Element) operations.item(i);
            var actions = operation.getElementsByTagNameNS(soap12, "operation");
            if ("nfeAutorizacaoLote".equals(operation.getAttribute("name")) && actions.getLength() == 1) {
                assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4/nfeAutorizacaoLote",
                        ((org.w3c.dom.Element) actions.item(0)).getAttribute("soapAction"));
                matched = true;
            }
        }
        assertTrue(matched);
    }

    @Test
    void wrapsOneSignedQrCodeNfceInSynchronousSoap12AndKeepsSignatureValid() throws Exception {
        Fixture fixture = fixture();
        String envelope = SefazSpNfceProvider.envelope(fixture.request());
        Document soap = parse(envelope);
        assertEquals("http://www.w3.org/2003/05/soap-envelope", soap.getDocumentElement().getNamespaceURI());
        assertEquals("1", soap.getElementsByTagNameNS(NFE_NS, "indSinc").item(0).getTextContent());
        assertEquals(fixture.request().accessKey().substring(29),
                soap.getElementsByTagNameNS(NFE_NS, "idLote").item(0).getTextContent());
        assertEquals(1, soap.getElementsByTagNameNS(NFE_NS, "NFe").getLength());
        assertEquals(1, soap.getElementsByTagNameNS(NFE_NS, "infNFeSupl").getLength());
        var inf = (org.w3c.dom.Element) soap.getElementsByTagNameNS(NFE_NS, "infNFe").item(0);
        inf.setIdAttribute("Id", true);
        var signature = (org.w3c.dom.Element) soap.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext context = new DOMValidateContext(fixture.certificate().getPublicKey(), signature);
        context.setProperty("org.jcp.xml.dsig.secureValidation", false);
        assertTrue(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context));
    }

    @Test
    void sendsExactlyOnceToFixedHomologationEndpointAndReturnsRawReply() throws Exception {
        Fixture fixture = fixture();
        AtomicInteger sends = new AtomicInteger();
        SefazSpNfceProvider provider = new SefazSpNfceProvider(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                (endpoint, action, envelope) -> {
                    sends.incrementAndGet();
                    assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeAutorizacao4.asmx",
                            endpoint.toString());
                    assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4/nfeAutorizacaoLote",
                            action);
                    assertTrue(envelope.contains("<enviNFe"));
                    return new SefazSpNfceProvider.Transport.Reply(200, "<soap-response/>");
                });

        assertEquals("<soap-response/>", provider.authorize(fixture.request()).rawXml());
        assertEquals(1, sends.get());
    }

    @Test
    void refusesProductionAndAmbiguousOrInvalidSubmissionWithoutRetry() throws Exception {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class, () -> new SefazSpNfceProvider(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.PRODUCTION, true),
                (endpoint, action, envelope) -> new SefazSpNfceProvider.Transport.Reply(200, "ok")));
        assertThrows(IllegalArgumentException.class,
                () -> SefazSpNfceProvider.envelope(new FiscalAuthorizationRequest(UUID.randomUUID(),
                        "1" + fixture.request().accessKey().substring(1), fixture.request().signedXml())));
        assertThrows(IllegalArgumentException.class,
                () -> SefazSpNfceProvider.envelope(new FiscalAuthorizationRequest(UUID.randomUUID(),
                        fixture.request().accessKey(), fixture.request().signedXml()
                                .replace("<tpAmb>2", "<tpAmb>1"))));
        assertThrows(IllegalArgumentException.class,
                () -> SefazSpNfceProvider.envelope(new FiscalAuthorizationRequest(UUID.randomUUID(),
                        fixture.request().accessKey(), fixture.request().signedXml()
                                .replace("|3|2", "|2|2"))));
        assertThrows(IllegalArgumentException.class,
                () -> SefazSpNfceProvider.envelope(new FiscalAuthorizationRequest(UUID.randomUUID(),
                        fixture.request().accessKey(), "<!DOCTYPE NFe>" + fixture.request().signedXml())));
        AtomicInteger sends = new AtomicInteger();
        SefazSpNfceProvider provider = new SefazSpNfceProvider(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                (endpoint, action, envelope) -> {
                    sends.incrementAndGet();
                    throw new IOException("network failure with potentially sensitive details");
                });
        FiscalSubmissionUncertainException error = assertThrows(FiscalSubmissionUncertainException.class,
                () -> provider.authorize(fixture.request()));
        assertEquals(1, sends.get());
        assertTrue(!error.getMessage().contains("sensitive"));
    }

    private static Fixture fixture() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
        var address = new NfceXmlBuilder.Address("Rua Exemplo", "100", "Centro",
                "3550308", "Sao Paulo", "01001000");
        var issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", address);
        var zero = new NfceXmlBuilder.OtherContribution(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var taxes = new NfceXmlBuilder.Taxes("0", "102", "99", "99", zero, zero);
        var item = new NfceXmlBuilder.Item("P1", "Cafe", "SEM GTIN", "19059090", "0300100",
                "5102", "UN", new BigDecimal("1.0000"), new BigDecimal("12.5000"),
                new BigDecimal("12.50"), taxes);
        var draft = new NfceXmlBuilder.Draft(issuer, "VENDA", 1, 123, "12345678",
                OffsetDateTime.parse("2026-10-18T12:00:00-03:00"), List.of(item), new BigDecimal("12.50"));
        NfceXmlBuilder.Result result = new NfceXmlBuilder().buildHomologation(draft);
        String signed = new NfceXmlSigner().signHomologation(result.unsignedXml(), pair.getPrivate(), certificate);
        String withQr = new NfceQrCodeV3().addToSignedHomologation(signed);
        return new Fixture(new FiscalAuthorizationRequest(UUID.randomUUID(), result.accessKey(), withQr), certificate);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private record Fixture(FiscalAuthorizationRequest request, java.security.cert.X509Certificate certificate) { }
}
