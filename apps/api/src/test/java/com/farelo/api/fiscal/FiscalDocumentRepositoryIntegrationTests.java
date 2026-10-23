package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.fiscal.web.FiscalDocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link FiscalDocument} maps correctly onto the table created
 * by {@code V31__create_fiscal_document_table.sql}, against a real
 * PostgreSQL instance — including that it round-trips against a real
 * {@link Command}, defaults to {@link FiscalDocumentStatus#PENDING} with
 * every identifying field {@code null}, that its mutable fields persist
 * correctly once set, and that {@code findByCommandOrderByCreatedAtAsc}
 * returns rows oldest-first, scoped to the right command. Same format as
 * {@code PaymentRepositoryIntegrationTests}.
 *
 * <p>Uses dedicated seeded command numbers 21, 23-25 — free numbers per the
 * registry maintained across this suite (see e.g. {@code
 * PaymentRepositoryIntegrationTests}: 44-45, 48, 67-69; {@code
 * PaymentControllerIntegrationTests}: 4-7, 46-47, 49, 60-66, 70-77, 92).
 * {@code SEEDED_COMMAND_NUMBER} (21) is shared by tests whose changes are
 * rolled back after each method. This matters now that access_key is unique:
 * several recorder tests intentionally reuse the same signed XML fixture.
 * {@link
 * #findByCommandOrderByCreatedAtAscReturnsOldestFirstScopedToCommand()}
 * *does* assert an exact count ({@code hasSize(2)}), so it cannot share
 * {@code SEEDED_COMMAND_NUMBER} — it gets its own untouched pair, {@code
 * ORDER_COMMAND_NUMBER} (24) and {@code ORDER_OTHER_COMMAND_NUMBER} (25),
 * that no other method in this class ever writes to. {@code
 * EMPTY_COMMAND_NUMBER} (23) is likewise dedicated to the "command with no
 * fiscal documents" test, for the same "must stay pristine" reason.
 *
 * <p>Each method rolls back its fixture; no table cleanup is needed.
 */
@SpringBootTest
@Transactional
class FiscalDocumentRepositoryIntegrationTests extends AbstractIntegrationTest {

    private static final int SEEDED_COMMAND_NUMBER = 21;
    private static final int EMPTY_COMMAND_NUMBER = 23;
    private static final int ORDER_COMMAND_NUMBER = 24;
    private static final int ORDER_OTHER_COMMAND_NUMBER = 25;

    @Autowired
    private FiscalDocumentRepository fiscalDocumentRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private FiscalAuthorizationRecorder authorizationRecorder;

    @Autowired
    private FiscalRejectionRecorder rejectionRecorder;

    @Autowired
    private FiscalCancellationRecorder cancellationRecorder;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    @Test
    void savesAndFindsFiscalDocumentWithDefaults() {
        Command command = command(SEEDED_COMMAND_NUMBER);

        FiscalDocument saved = fiscalDocumentRepository.saveAndFlush(new FiscalDocument(command));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCommand().getId()).isEqualTo(command.getId());
        assertThat(saved.getStatus()).isEqualTo(FiscalDocumentStatus.PENDING);
        assertThat(saved.getDocumentNumber()).isNull();
        assertThat(saved.getSeries()).isNull();
        assertThat(saved.getAccessKey()).isNull();
        assertThat(saved.getProtocolNumber()).isNull();
        assertThat(saved.getXmlContent()).isNull();
        assertThat(saved.getAuthorizedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<FiscalDocument> found = fiscalDocumentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(FiscalDocumentStatus.PENDING);
        assertThat(found.get().getCommand().getId()).isEqualTo(command.getId());
    }

    @Test
    @Transactional
    void locksFiscalDocumentByIdBeforeRecordingAuthorization() {
        FiscalDocument saved = fiscalDocumentRepository.saveAndFlush(
                new FiscalDocument(command(SEEDED_COMMAND_NUMBER)));

        assertThat(fiscalDocumentRepository.findByIdForUpdate(saved.getId()))
                .isPresent().get().extracting(FiscalDocument::getId).isEqualTo(saved.getId());
    }

    @Test
    void persistsConfirmedAuthorizationAtomically() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        FiscalDocument document = new FiscalDocument(command(SEEDED_COMMAND_NUMBER));
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey(fixture.request().accessKey());
        document = fiscalDocumentRepository.saveAndFlush(document);
        var request = new FiscalAuthorizationRequest(document.getId(),
                fixture.request().accessKey(), fixture.request().signedXml());

        authorizationRecorder.record(request, new FiscalAuthorizationResponse(
                FiscalAuthorizationRecorderTests.reply(request.accessKey(), "100")));

        FiscalDocument saved = fiscalDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FiscalDocumentStatus.AUTHORIZED);
        assertThat(saved.getProtocolNumber()).isEqualTo("135260000000001");
        assertThat(saved.getAuthorizedAt()).isNotNull();
        assertThat(saved.getXmlContent()).contains("nfeProc", "protNFe");
    }

    @Test
    void persistsDefiniteRejectionWithCodeReasonAndRawResponse() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        FiscalDocument document = new FiscalDocument(command(SEEDED_COMMAND_NUMBER));
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey(fixture.request().accessKey());
        document = fiscalDocumentRepository.saveAndFlush(document);
        var request = new FiscalAuthorizationRequest(document.getId(),
                fixture.request().accessKey(), fixture.request().signedXml());
        String reply = FiscalAuthorizationRecorderTests.reply(request.accessKey(), "215");

        rejectionRecorder.record(request, new FiscalAuthorizationResponse(reply));

        FiscalDocument saved = fiscalDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FiscalDocumentStatus.REJECTED);
        assertThat(saved.getRejectionCode()).isEqualTo("215");
        assertThat(saved.getRejectionReason()).startsWith("Rejeição:");
        assertThat(saved.getRejectionResponseXml()).isEqualTo(reply);
        assertThat(saved.getXmlContent()).isEqualTo(request.signedXml());
        assertThat(saved.getProtocolNumber()).isNull();
        assertThat(saved.getAuthorizedAt()).isNull();
    }

    @Test
    void persistsConfirmedCancellationWithoutReplacingAuthorizedXml() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        FiscalDocument document = new FiscalDocument(command(SEEDED_COMMAND_NUMBER));
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setAccessKey(fixture.request().accessKey());
        document.setProtocolNumber("135260000000001");
        document.setXmlContent("<nfeProc>authorized original</nfeProc>");
        document = fiscalDocumentRepository.saveAndFlush(document);
        String reply = FiscalCancellationRecorderTests.reply(document.getAccessKey(), "135", "110111");

        cancellationRecorder.record(FiscalCancellationRecorderTests.request(
                document.getId(), document.getAccessKey(), document.getProtocolNumber()),
                new FiscalCancellationResponse(reply));

        FiscalDocument saved = fiscalDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FiscalDocumentStatus.CANCELLED);
        assertThat(saved.getXmlContent()).isEqualTo("<nfeProc>authorized original</nfeProc>");
        assertThat(saved.getCancellationProtocolNumber()).isEqualTo("135260000000002");
        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(saved.getCancellationEventXml()).contains("procEventoNFe", "retEvento");
        assertThat(saved.getCancellationResponseXml()).isEqualTo(reply);
        FiscalDocumentResponse admin = FiscalDocumentResponse.from(
                fiscalDocumentRepository.findByIdWithCommand(document.getId()).orElseThrow());
        assertThat(admin.cancellationEventXml()).contains("procEventoNFe", "retEvento");
        assertThat(admin.cancellationProtocolNumber()).isEqualTo("135260000000002");
    }

    @Test
    void consultationProtocolResolvesPriorUncertainAuthorization() throws Exception {
        var fixture = SefazSpNfceProviderTests.fixture();
        FiscalDocument document = new FiscalDocument(command(SEEDED_COMMAND_NUMBER));
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        document.setAccessKey(fixture.request().accessKey());
        document = fiscalDocumentRepository.saveAndFlush(document);
        var request = new FiscalAuthorizationRequest(document.getId(),
                fixture.request().accessKey(), fixture.request().signedXml());

        FiscalConsultationResult result = authorizationRecorder.reconcile(request,
                new FiscalConsultationResponse(FiscalConsultationReconciliationTests.reply(
                        request.accessKey(), "100", true)));

        assertThat(result).isEqualTo(FiscalConsultationResult.AUTHORIZED);
        FiscalDocument saved = fiscalDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FiscalDocumentStatus.AUTHORIZED);
        assertThat(saved.getProtocolNumber()).isEqualTo("13526000000000001");
        assertThat(saved.getXmlContent()).contains("nfeProc", "protNFe");
    }

    @Test
    void savesEveryFiscalDocumentStatus() {
        Command command = command(SEEDED_COMMAND_NUMBER);

        for (FiscalDocumentStatus status : FiscalDocumentStatus.values()) {
            FiscalDocument document = new FiscalDocument(command);
            document.setStatus(status);
            FiscalDocument saved = fiscalDocumentRepository.saveAndFlush(document);

            assertThat(fiscalDocumentRepository.findById(saved.getId()).orElseThrow().getStatus())
                    .isEqualTo(status);
        }
    }

    @Test
    void persistsMutatedIdentifyingFields() {
        Command command = command(SEEDED_COMMAND_NUMBER);
        FiscalDocument document = fiscalDocumentRepository.saveAndFlush(new FiscalDocument(command));

        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setDocumentNumber(123);
        document.setSeries(1);
        document.setAccessKey("12345678901234567890123456789012345678901234");
        document.setProtocolNumber("135240000000001");
        document.setXmlContent("<nfceProc></nfceProc>");
        OffsetDateTime authorizedAt = OffsetDateTime.now(ZoneOffset.UTC);
        document.setAuthorizedAt(authorizedAt);
        fiscalDocumentRepository.saveAndFlush(document);

        FiscalDocument found = fiscalDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(FiscalDocumentStatus.AUTHORIZED);
        assertThat(found.getDocumentNumber()).isEqualTo(123);
        assertThat(found.getSeries()).isEqualTo(1);
        assertThat(found.getAccessKey()).isEqualTo("12345678901234567890123456789012345678901234");
        assertThat(found.getProtocolNumber()).isEqualTo("135240000000001");
        assertThat(found.getXmlContent()).isEqualTo("<nfceProc></nfceProc>");
        assertThat(found.getAuthorizedAt()).isNotNull();
    }

    @Test
    void findByCommandOrderByCreatedAtAscReturnsOldestFirstScopedToCommand() throws InterruptedException {
        Command command = command(ORDER_COMMAND_NUMBER);
        Command otherCommand = command(ORDER_OTHER_COMMAND_NUMBER);

        FiscalDocument first = fiscalDocumentRepository.saveAndFlush(new FiscalDocument(command));
        // Distinct, increasing createdAt for a deterministic order assertion
        // — same pattern PaymentRepositoryIntegrationTests/
        // PrintJobControllerIntegrationTests already use.
        Thread.sleep(10);
        FiscalDocument second = fiscalDocumentRepository.saveAndFlush(new FiscalDocument(command));
        // A fiscal document on a different command must not leak into this
        // list.
        fiscalDocumentRepository.saveAndFlush(new FiscalDocument(otherCommand));

        List<FiscalDocument> documents = fiscalDocumentRepository.findByCommandOrderByCreatedAtAsc(command);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).getId()).isEqualTo(first.getId());
        assertThat(documents.get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    void findByCommandOrderByCreatedAtAscReturnsEmptyListForCommandWithNoFiscalDocuments() {
        Command command = command(EMPTY_COMMAND_NUMBER);

        assertThat(fiscalDocumentRepository.findByCommandOrderByCreatedAtAsc(command)).isEmpty();
    }

}
