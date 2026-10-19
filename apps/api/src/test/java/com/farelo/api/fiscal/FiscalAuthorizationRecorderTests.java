package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalAuthorizationRecorderTests {
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String PROTOCOL = "135260000000001";

    @Test
    void recordsMatchingAuthorizationAndPreservesSignedNfce() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var saves = new AtomicInteger();
        var document = new FiscalDocument(new Command(1));
        document.setAccessKey(fixture.request().accessKey());
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        var repository = repository(fixture.request().documentId(), document, saves);

        FiscalDocument saved = new FiscalAuthorizationRecorder(repository).record(
                fixture.request(), new FiscalAuthorizationResponse(reply(fixture.request().accessKey(), "100")));

        assertEquals(FiscalDocumentStatus.AUTHORIZED, saved.getStatus());
        assertEquals(PROTOCOL, saved.getProtocolNumber());
        assertEquals(OffsetDateTime.parse("2026-10-19T12:00:00-03:00"), saved.getAuthorizedAt());
        assertEquals(fixture.request().accessKey(), saved.getAccessKey());
        Document processed = parse(saved.getXmlContent());
        assertEquals("nfeProc", processed.getDocumentElement().getLocalName());
        assertEquals(1, processed.getElementsByTagNameNS(NFE_NS, "NFe").getLength());
        assertEquals(1, processed.getElementsByTagNameNS(NFE_NS, "protNFe").getLength());
        var inf = (org.w3c.dom.Element) processed.getElementsByTagNameNS(NFE_NS, "infNFe").item(0);
        inf.setIdAttribute("Id", true);
        var signature = (org.w3c.dom.Element) processed.getElementsByTagNameNS(
                XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext context = new DOMValidateContext(fixture.certificate().getPublicKey(), signature);
        context.setProperty("org.jcp.xml.dsig.secureValidation", false);
        assertTrue(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context));
        assertEquals(1, saves.get());
    }

    @Test
    void refusesRejectionAndMismatchedKeyBeforePersistence() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var saves = new AtomicInteger();
        var repository = repository(fixture.request().documentId(), null, saves);
        var recorder = new FiscalAuthorizationRecorder(repository);
        assertThrows(IllegalArgumentException.class, () -> recorder.record(fixture.request(),
                new FiscalAuthorizationResponse(reply(fixture.request().accessKey(), "204"))));
        assertThrows(IllegalArgumentException.class, () -> recorder.record(fixture.request(),
                new FiscalAuthorizationResponse(reply("35" + "0".repeat(42), "100"))));
        assertEquals(0, saves.get());
    }

    @Test
    void refusesAuthorizedResponseForWrongDocumentStateOrKey() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var saves = new AtomicInteger();
        var document = new FiscalDocument(new Command(2));
        document.setAccessKey(fixture.request().accessKey());
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        var repository = repository(fixture.request().documentId(), document, saves);
        var recorder = new FiscalAuthorizationRecorder(repository);
        var response = new FiscalAuthorizationResponse(reply(fixture.request().accessKey(), "100"));
        assertThrows(IllegalStateException.class, () -> recorder.record(fixture.request(), response));
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey("35" + "0".repeat(42));
        assertThrows(IllegalStateException.class, () -> recorder.record(fixture.request(), response));
        assertEquals(0, saves.get());
    }

    private static FiscalDocumentRepository repository(UUID id, FiscalDocument document, AtomicInteger saves) {
        return (FiscalDocumentRepository) Proxy.newProxyInstance(
                FiscalDocumentRepository.class.getClassLoader(),
                new Class<?>[]{FiscalDocumentRepository.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findByIdForUpdate" -> id.equals(args[0]) ? Optional.ofNullable(document) : Optional.empty();
                        case "save" -> { saves.incrementAndGet(); yield args[0]; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    static String reply(String key, String noteStatus) {
        return """
                <Envelope xmlns="http://www.w3.org/2003/05/soap-envelope"><Body>
                  <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4">
                    <retEnviNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
                      <tpAmb>2</tpAmb><verAplic>SP_TESTE</verAplic><cStat>104</cStat>
                      <xMotivo>Lote processado</xMotivo><cUF>35</cUF>
                      <dhRecbto>2026-10-19T12:00:00-03:00</dhRecbto>
                      <protNFe versao="4.00"><infProt>
                        <tpAmb>2</tpAmb><verAplic>SP_TESTE</verAplic><chNFe>%s</chNFe>
                        <dhRecbto>2026-10-19T12:00:00-03:00</dhRecbto>
                        <nProt>%s</nProt><cStat>%s</cStat><xMotivo>Autorizado</xMotivo>
                      </infProt></protNFe>
                    </retEnviNFe>
                  </nfeResultMsg>
                </Body></Envelope>
                """.formatted(key, PROTOCOL, noteStatus);
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
