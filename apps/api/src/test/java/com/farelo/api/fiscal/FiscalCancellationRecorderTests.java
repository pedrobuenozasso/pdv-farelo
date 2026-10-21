package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalCancellationRecorderTests {
    private static final String KEY = "35261011222333000181650010000000011123456789";
    private static final String ORIGINAL_PROTOCOL = "135260000000001";

    @Test
    void recordsOnlyLinkedCancellationAndPreservesAuthorizedNfce() {
        UUID id = UUID.randomUUID();
        FiscalDocument document = authorized();
        AtomicInteger saves = new AtomicInteger();
        var recorder = new FiscalCancellationRecorder(
                FiscalAuthorizationRecorderTests.repository(id, document, saves));

        FiscalDocument saved = recorder.record(request(id, KEY, ORIGINAL_PROTOCOL),
                new FiscalCancellationResponse(reply(KEY, "135", "110111")));

        assertEquals(FiscalDocumentStatus.CANCELLED, saved.getStatus());
        assertEquals("<nfeProc>authorized original</nfeProc>", saved.getXmlContent());
        assertEquals(ORIGINAL_PROTOCOL, saved.getProtocolNumber());
        assertEquals("135260000000002", saved.getCancellationProtocolNumber());
        assertEquals("2026-10-21T12:00-03:00", saved.getCancelledAt().toString());
        assertTrue(saved.getCancellationEventXml().contains("procEventoNFe"));
        assertTrue(saved.getCancellationResponseXml().contains("retEnvEvento"));
        assertEquals(1, saves.get());
    }

    @Test
    void refusesUnlinkedOrOtherEventWithoutMutation() {
        UUID id = UUID.randomUUID();
        FiscalDocument document = authorized();
        AtomicInteger saves = new AtomicInteger();
        var recorder = new FiscalCancellationRecorder(
                FiscalAuthorizationRecorderTests.repository(id, document, saves));
        for (var response : new FiscalCancellationResponse[]{
                new FiscalCancellationResponse(reply(KEY, "136", "110111")),
                new FiscalCancellationResponse(reply(KEY, "573", "110111")),
                new FiscalCancellationResponse(reply(KEY, "135", "110110")),
                new FiscalCancellationResponse(reply("35" + "0".repeat(42), "135", "110111"))}) {
            assertThrows(IllegalArgumentException.class,
                    () -> recorder.record(request(id, KEY, ORIGINAL_PROTOCOL), response));
        }
        assertEquals(FiscalDocumentStatus.AUTHORIZED, document.getStatus());
        assertNull(document.getCancellationEventXml());
        assertEquals(0, saves.get());
    }

    @Test
    void refusesUnsignedEventWrongProtocolOrWrongDocumentState() {
        UUID id = UUID.randomUUID();
        FiscalDocument document = authorized();
        AtomicInteger saves = new AtomicInteger();
        var recorder = new FiscalCancellationRecorder(
                FiscalAuthorizationRecorderTests.repository(id, document, saves));
        var response = new FiscalCancellationResponse(reply(KEY, "135", "110111"));
        var request = request(id, KEY, ORIGINAL_PROTOCOL);
        assertThrows(IllegalArgumentException.class, () -> recorder.record(
                new FiscalCancellationRequest(id, KEY,
                        request.signedEventXml().replace("<Signature", "<OtherSignature")), response));
        assertThrows(IllegalStateException.class, () -> recorder.record(
                request(id, KEY, "135260000000099"), response));
        document.setStatus(FiscalDocumentStatus.REJECTED);
        assertThrows(IllegalStateException.class, () -> recorder.record(request, response));
        assertEquals(0, saves.get());
    }

    private static FiscalDocument authorized() {
        FiscalDocument document = new FiscalDocument(new Command(1));
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setAccessKey(KEY);
        document.setProtocolNumber(ORIGINAL_PROTOCOL);
        document.setXmlContent("<nfeProc>authorized original</nfeProc>");
        return document;
    }

    static FiscalCancellationRequest request(UUID id, String key, String protocol) {
        String xml = """
                <envEvento xmlns="http://www.portalfiscal.inf.br/nfe" versao="1.00">
                 <idLote>1</idLote><evento versao="1.00">
                  <infEvento Id="ID110111%s01">
                    <cOrgao>35</cOrgao><tpAmb>2</tpAmb><CNPJ>%s</CNPJ>
                    <chNFe>%s</chNFe><dhEvento>2026-10-21T11:59:00-03:00</dhEvento>
                    <tpEvento>110111</tpEvento><nSeqEvento>1</nSeqEvento><verEvento>1.00</verEvento>
                    <detEvento versao="1.00"><descEvento>Cancelamento</descEvento>
                      <nProt>%s</nProt><xJust>Operacao de teste cancelada</xJust>
                    </detEvento>
                  </infEvento>
                  <Signature xmlns="http://www.w3.org/2000/09/xmldsig#"/>
                </evento></envEvento>
                """.formatted(key, key.substring(6, 20), key, protocol);
        return new FiscalCancellationRequest(id, key, xml);
    }

    static String reply(String key, String status, String eventType) {
        return """
                <Envelope xmlns="http://www.w3.org/2003/05/soap-envelope"><Body>
                  <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4">
                    <retEnvEvento xmlns="http://www.portalfiscal.inf.br/nfe" versao="1.00">
                      <idLote>1</idLote><tpAmb>2</tpAmb><verAplic>SP_TESTE</verAplic>
                      <cOrgao>35</cOrgao><cStat>128</cStat><xMotivo>Lote de Evento Processado</xMotivo>
                      <retEvento versao="1.00"><infEvento>
                        <tpAmb>2</tpAmb><verAplic>SP_TESTE</verAplic><cOrgao>35</cOrgao>
                        <cStat>%s</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>
                        <chNFe>%s</chNFe><tpEvento>%s</tpEvento>
                        <xEvento>Cancelamento registrado</xEvento><nSeqEvento>1</nSeqEvento>
                        <dhRegEvento>2026-10-21T12:00:00-03:00</dhRegEvento>
                        <nProt>135260000000002</nProt>
                      </infEvento></retEvento>
                    </retEnvEvento>
                  </nfeResultMsg>
                </Body></Envelope>
                """.formatted(status, key, eventType);
    }
}
