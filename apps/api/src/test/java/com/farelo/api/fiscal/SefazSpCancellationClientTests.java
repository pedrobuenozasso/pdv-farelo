package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SefazSpCancellationClientTests {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";

    @Test
    void authenticatedPublicWsdlMatchesFixedEventEndpointAndAction() throws Exception {
        String wsdl = "http://schemas.xmlsoap.org/wsdl/";
        String soap12 = "http://schemas.xmlsoap.org/wsdl/soap12/";
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document contract;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/sefaz-sp-nfce-evento4-homologacao.wsdl"))) {
            contract = factory.newDocumentBuilder().parse(input);
        }
        var address = (Element) contract.getElementsByTagNameNS(soap12, "address").item(0);
        assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeRecepcaoEvento4.asmx",
                address.getAttribute("location"));
        var operations = contract.getElementsByTagNameNS(wsdl, "operation");
        boolean found = false;
        for (int i = 0; i < operations.getLength(); i++) {
            var operation = (Element) operations.item(i);
            var actions = operation.getElementsByTagNameNS(soap12, "operation");
            if ("nfeRecepcaoEventoNF".equals(operation.getAttribute("name")) && actions.getLength() == 1) {
                assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEventoNF",
                        ((Element) actions.item(0)).getAttribute("soapAction"));
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    void wrapsOneSignedEventWithoutInvalidatingSignatureAndSendsOnce() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
        String key = SefazSpNfceProviderTests.fixture().request().accessKey();
        String signed = new NfceCancellationEventSigner().signHomologation(
                new NfceCancellationEventSigner.Draft(key, key.substring(6, 20),
                        "135260000000001", "Venda de teste desfeita antes da entrega",
                        OffsetDateTime.parse("2026-10-21T12:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-21T11:45:00-03:00"), false, 7),
                pair.getPrivate(), certificate);
        var fiscal = new FiscalCancellationRequest(UUID.randomUUID(), key, signed);
        AtomicInteger sends = new AtomicInteger();
        var client = new SefazSpCancellationClient(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                (endpoint, action, envelope) -> {
                    sends.incrementAndGet();
                    assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeRecepcaoEvento4.asmx",
                            endpoint.toString());
                    assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEventoNF",
                            action);
                    assertWrappedSignatureValid(envelope, certificate.getPublicKey());
                    return new SefazSpCancellationClient.Transport.Reply(200, "<reply/>");
                });
        assertEquals("<reply/>", client.send(fiscal).rawXml());
        assertEquals(1, sends.get());
    }

    @Test
    void refusesProductionAndDoesNotRetryUncertainSubmission() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new SefazSpCancellationClient(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.PRODUCTION, true),
                (endpoint, action, envelope) -> new SefazSpCancellationClient.Transport.Reply(200, "ok")));
        AtomicInteger sends = new AtomicInteger();
        var client = new SefazSpCancellationClient(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                (endpoint, action, envelope) -> {
                    sends.incrementAndGet();
                    throw new IOException("sensitive transport detail");
                });
        String key = SefazSpNfceProviderTests.fixture().request().accessKey();
        FiscalCancellationRequest request = FiscalCancellationRecorderTests.request(
                UUID.randomUUID(), key, "135260000000001");
        FiscalSubmissionUncertainException error = assertThrows(FiscalSubmissionUncertainException.class,
                () -> client.send(request));
        assertEquals(1, sends.get());
        assertTrue(!error.getMessage().contains("sensitive"));
    }

    private static void assertWrappedSignatureValid(String envelope, java.security.PublicKey publicKey) {
        try {
            Document soap = SefazSpAuthorizationParser.parseXml(envelope);
            assertEquals("http://www.w3.org/2003/05/soap-envelope",
                    soap.getDocumentElement().getNamespaceURI());
            assertEquals(1, soap.getElementsByTagNameNS(NFE_NS, "evento").getLength());
            Element info = (Element) soap.getElementsByTagNameNS(NFE_NS, "infEvento").item(0);
            info.setIdAttribute("Id", true);
            Element signature = (Element) soap.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
            DOMValidateContext context = new DOMValidateContext(publicKey, signature);
            context.setProperty("org.jcp.xml.dsig.secureValidation", false);
            assertTrue(XMLSignatureFactory.getInstance("DOM")
                    .unmarshalXMLSignature(context).validate(context));
        } catch (Exception ex) {
            throw new AssertionError("SOAP wrapper invalidated cancellation signature", ex);
        }
    }
}
