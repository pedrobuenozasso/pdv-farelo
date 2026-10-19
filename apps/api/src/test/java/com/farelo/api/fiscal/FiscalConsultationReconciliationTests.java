package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalConsultationReconciliationTests {

    @Test
    void matchingConsultationProtocolResolvesUncertainDocumentAsAuthorized() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var document = new FiscalDocument(new Command(1));
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey(fixture.request().accessKey());
        var saves = new AtomicInteger();
        var repository = FiscalAuthorizationRecorderTests.repository(
                fixture.request().documentId(), document, saves);
        var recorder = new FiscalAuthorizationRecorder(repository);

        assertEquals(FiscalConsultationResult.AUTHORIZED, recorder.reconcile(fixture.request(),
                new FiscalConsultationResponse(reply(fixture.request().accessKey(), "100", true))));
        assertEquals(FiscalDocumentStatus.AUTHORIZED, document.getStatus());
        assertEquals("13526000000000001", document.getProtocolNumber());
        assertEquals(1, saves.get());
    }

    @Test
    void notFoundOrOtherStatusNeverAuthorizesOrRetries() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var saves = new AtomicInteger();
        var repository = FiscalAuthorizationRecorderTests.repository(
                fixture.request().documentId(), null, saves);
        var recorder = new FiscalAuthorizationRecorder(repository);
        String key = fixture.request().accessKey();

        assertEquals(FiscalConsultationResult.NOT_FOUND,
                recorder.reconcile(fixture.request(), new FiscalConsultationResponse(reply(key, "217", false))));
        assertEquals(FiscalConsultationResult.UNRESOLVED,
                recorder.reconcile(fixture.request(), new FiscalConsultationResponse(reply(key, "101", false))));
        assertThrows(IllegalArgumentException.class, () -> recorder.reconcile(fixture.request(),
                new FiscalConsultationResponse(reply("35" + "0".repeat(42), "100", true))));
        assertThrows(IllegalArgumentException.class, () -> recorder.reconcile(fixture.request(),
                new FiscalConsultationResponse(reply(key, "100", false))));
        assertEquals(0, saves.get());
    }

    static String reply(String key, String status, boolean withProtocol) {
        String protocol = withProtocol ? """
                <protNFe versao="4.00"><infProt>
                <tpAmb>2</tpAmb><verAplic>SP_TESTE</verAplic><chNFe>%s</chNFe>
                <dhRecbto>2026-10-19T12:00:00-03:00</dhRecbto>
                <nProt>13526000000000001</nProt><cStat>100</cStat>
                <xMotivo>Autorizado o uso da NFC-e</xMotivo>
                </infProt></protNFe>
                """.formatted(key) : "";
        return """
                <Envelope xmlns="http://www.w3.org/2003/05/soap-envelope"><Body>
                <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4">
                <retConsSitNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
                <tpAmb>2</tpAmb><verAplic>SP_TESTE</verAplic><cStat>%s</cStat>
                <xMotivo>Resultado da consulta</xMotivo><cUF>35</cUF><chNFe>%s</chNFe>
                %s
                </retConsSitNFe></nfeResultMsg></Body></Envelope>
                """.formatted(status, key, protocol);
    }
}
