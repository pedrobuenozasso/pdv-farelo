package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SefazSpProtocolConsultationClientTests {

    @Test
    void authenticatedPublicWsdlMatchesConsultationEndpointAndSoapAction() throws Exception {
        String wsdl = "http://schemas.xmlsoap.org/wsdl/";
        String soap12 = "http://schemas.xmlsoap.org/wsdl/soap12/";
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document contract;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/sefaz-sp-nfce-consulta4-homologacao.wsdl"))) {
            contract = factory.newDocumentBuilder().parse(input);
        }
        var address = (org.w3c.dom.Element) contract.getElementsByTagNameNS(soap12, "address").item(0);
        assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeConsultaProtocolo4.asmx",
                address.getAttribute("location"));
        var operations = contract.getElementsByTagNameNS(wsdl, "operation");
        boolean matched = false;
        for (int i = 0; i < operations.getLength(); i++) {
            var operation = (org.w3c.dom.Element) operations.item(i);
            var actions = operation.getElementsByTagNameNS(soap12, "operation");
            if ("nfeConsultaNF".equals(operation.getAttribute("name")) && actions.getLength() == 1) {
                assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4/nfeConsultaNF",
                        ((org.w3c.dom.Element) actions.item(0)).getAttribute("soapAction"));
                matched = true;
            }
        }
        assertTrue(matched);
    }

    @Test
    void queriesOneKeyAtFixedHomologationServiceWithoutSendingAnNfce() throws Exception {
        String key = SefazSpNfceProviderTests.fixture().request().accessKey();
        AtomicInteger sends = new AtomicInteger();
        var client = new SefazSpProtocolConsultationClient(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                (endpoint, action, envelope) -> {
                    sends.incrementAndGet();
                    assertEquals("https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeConsultaProtocolo4.asmx",
                            endpoint.toString());
                    assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4/nfeConsultaNF",
                            action);
                    assertTrue(envelope.contains("<consSitNFe"));
                    assertTrue(envelope.contains("<chNFe>" + key + "</chNFe>"));
                    assertTrue(!envelope.contains("<NFe"));
                    return new SefazSpProtocolConsultationClient.Transport.Reply(200, "<query-reply/>");
                });

        assertEquals("<query-reply/>", client.consult(key).rawXml());
        assertEquals(1, sends.get());
    }

    @Test
    void refusesProductionBadKeyAndNetworkFailureWithoutRetry() throws Exception {
        String key = SefazSpNfceProviderTests.fixture().request().accessKey();
        assertThrows(IllegalArgumentException.class, () -> new SefazSpProtocolConsultationClient(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.PRODUCTION, true),
                (endpoint, action, envelope) -> new SefazSpProtocolConsultationClient.Transport.Reply(200, "ok")));
        AtomicInteger sends = new AtomicInteger();
        var client = new SefazSpProtocolConsultationClient(
                new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                (endpoint, action, envelope) -> {
                    sends.incrementAndGet();
                    throw new IOException("potentially sensitive provider detail");
                });
        assertThrows(IllegalArgumentException.class, () -> client.consult("35" + "0".repeat(42)));
        var failure = assertThrows(FiscalConsultationUnavailableException.class, () -> client.consult(key));
        assertEquals(1, sends.get());
        assertTrue(!failure.getMessage().contains("sensitive"));
    }
}
