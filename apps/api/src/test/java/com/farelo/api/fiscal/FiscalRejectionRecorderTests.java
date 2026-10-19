package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalRejectionRecorderTests {

    @Test
    void recordsDefinitePerNoteRejectionWithOriginalEvidence() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var document = new FiscalDocument(new Command(1));
        document.setAccessKey(fixture.request().accessKey());
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        var saves = new AtomicInteger();
        var repository = FiscalAuthorizationRecorderTests.repository(
                fixture.request().documentId(), document, saves);
        String soap = FiscalAuthorizationRecorderTests.reply(fixture.request().accessKey(), "215");

        FiscalDocument result = new FiscalRejectionRecorder(repository).record(
                fixture.request(), new FiscalAuthorizationResponse(soap));

        assertEquals(FiscalDocumentStatus.REJECTED, result.getStatus());
        assertEquals("215", result.getRejectionCode());
        assertEquals("Rejeição: Falha no Schema XML da NFC-e", result.getRejectionReason());
        assertEquals(soap, result.getRejectionResponseXml());
        assertEquals(fixture.request().signedXml(), result.getXmlContent());
        assertNull(result.getProtocolNumber());
        assertNull(result.getAuthorizedAt());
        assertEquals(1, saves.get());
    }

    @Test
    void duplicateOrAuthorizationStatusNeedsReconciliationAndDoesNotWrite() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var saves = new AtomicInteger();
        var repository = FiscalAuthorizationRecorderTests.repository(
                fixture.request().documentId(), null, saves);
        var recorder = new FiscalRejectionRecorder(repository);
        for (String status : new String[]{"100", "204", "539"}) {
            assertThrows(IllegalArgumentException.class, () -> recorder.record(fixture.request(),
                    new FiscalAuthorizationResponse(
                            FiscalAuthorizationRecorderTests.reply(fixture.request().accessKey(), status))));
        }
        assertEquals(0, saves.get());
    }

    @Test
    void wrongKeyOrDocumentStateCannotBeRejected() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        var document = new FiscalDocument(new Command(2));
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setAccessKey(fixture.request().accessKey());
        var saves = new AtomicInteger();
        var repository = FiscalAuthorizationRecorderTests.repository(
                fixture.request().documentId(), document, saves);
        var recorder = new FiscalRejectionRecorder(repository);
        var response = new FiscalAuthorizationResponse(
                FiscalAuthorizationRecorderTests.reply(fixture.request().accessKey(), "215"));

        assertThrows(IllegalStateException.class, () -> recorder.record(fixture.request(), response));
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey("35" + "0".repeat(42));
        assertThrows(IllegalStateException.class, () -> recorder.record(fixture.request(), response));
        assertThrows(IllegalArgumentException.class, () -> recorder.record(fixture.request(),
                new FiscalAuthorizationResponse(FiscalAuthorizationRecorderTests.reply(
                        "35" + "0".repeat(42), "215"))));
        assertEquals(0, saves.get());
    }
}
