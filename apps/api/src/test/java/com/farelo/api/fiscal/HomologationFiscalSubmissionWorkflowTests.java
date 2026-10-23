package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HomologationFiscalSubmissionWorkflowTests {
    @Test
    void schemaFailurePreventsReservationAndNetwork() throws Exception {
        var fixture = new Fixture(xml -> { throw new IllegalArgumentException("invalid schema"); }, "ok", "ok");
        assertThrows(IllegalArgumentException.class, () -> fixture.workflow.submitOnce(fixture.request));
        assertEquals(0, fixture.attempts.reservations.get());
        assertEquals(0, fixture.sends.get());
    }

    @Test
    void uncertainSendConsultsByKeyAndNeverSendsAgain() throws Exception {
        var fixture = new Fixture(xml -> { }, null, "not_found");
        assertEquals(HomologationFiscalSubmissionWorkflow.Outcome.NOT_FOUND,
                fixture.workflow.submitOnce(fixture.request));
        assertEquals(FiscalSubmissionAttemptStatus.UNCERTAIN, fixture.attempts.attempt.getStatus());
        assertEquals(HomologationFiscalSubmissionWorkflow.Outcome.NOT_FOUND,
                fixture.workflow.reconcileOnly(fixture.request));
        assertEquals(1, fixture.sends.get());
        assertEquals(2, fixture.consults.get());
    }

    @Test
    void authenticatedReplyClosesFenceAndCannotBeSentAgain() throws Exception {
        var fixture = new Fixture(xml -> { }, "authorized", "ok");
        fixture.reply = FiscalAuthorizationRecorderTests.reply(fixture.request.accessKey(), "100");
        assertEquals(HomologationFiscalSubmissionWorkflow.Outcome.AUTHORIZED,
                fixture.workflow.submitOnce(fixture.request));
        assertEquals(FiscalSubmissionAttemptStatus.RESOLVED, fixture.attempts.attempt.getStatus());
        assertEquals(HomologationFiscalSubmissionWorkflow.Outcome.ALREADY_RESOLVED,
                fixture.workflow.reconcileOnly(fixture.request));
        assertEquals(1, fixture.sends.get());
    }

    @Test
    void malformedAuthorizationReplyRequiresConsultationInsteadOfRejectionOrResend() throws Exception {
        var fixture = new Fixture(xml -> { }, "<not-a-sefaz-reply/>", "not_found");
        assertEquals(HomologationFiscalSubmissionWorkflow.Outcome.NOT_FOUND,
                fixture.workflow.submitOnce(fixture.request));
        assertEquals(FiscalSubmissionAttemptStatus.UNCERTAIN, fixture.attempts.attempt.getStatus());
        assertEquals(1, fixture.sends.get());
        assertEquals(1, fixture.consults.get());
    }

    @Test
    void refusesAnOuterTransactionBeforeNetwork() throws Exception {
        var fixture = new Fixture(xml -> { }, "ok", "ok");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> fixture.workflow.submitOnce(fixture.request));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertEquals(0, fixture.sends.get());
    }

    private static final class Fixture {
        final FiscalAuthorizationRequest request;
        final FakeAttempts attempts = new FakeAttempts();
        final AtomicInteger sends = new AtomicInteger();
        final AtomicInteger consults = new AtomicInteger();
        final HomologationFiscalSubmissionWorkflow workflow;
        String reply;

        Fixture(Consumer<String> schemaCheck, String providerReply, String consultationReply) throws Exception {
            request = SefazSpNfceProviderTests.fixture().request();
            reply = providerReply;
            var consultation = new SefazSpProtocolConsultationClient(
                    new SefazSpNfceEnvironment(SefazSpNfceEnvironment.Mode.HOMOLOGATION, false),
                    (endpoint, action, envelope) -> {
                        consults.incrementAndGet();
                        String body = "not_found".equals(consultationReply)
                                ? FiscalConsultationReconciliationTests.reply(request.accessKey(), "217", false)
                                : consultationReply;
                        return new SefazSpProtocolConsultationClient.Transport.Reply(200, body);
                    });
            FiscalProvider provider = req -> {
                sends.incrementAndGet();
                if (reply == null) throw new FiscalSubmissionUncertainException();
                return new FiscalAuthorizationResponse(reply);
            };
            var authorized = new FiscalAuthorizationRecorder(null) {
                @Override public FiscalDocument record(FiscalAuthorizationRequest req, FiscalAuthorizationResponse res) {
                    return null;
                }
                @Override public FiscalConsultationResult reconcile(FiscalAuthorizationRequest req,
                        FiscalConsultationResponse res) {
                    return SefazSpConsultationParser.parse(req, res).kind()
                            == SefazSpConsultationParser.Kind.NOT_FOUND
                            ? FiscalConsultationResult.NOT_FOUND : FiscalConsultationResult.UNRESOLVED;
                }
            };
            workflow = new HomologationFiscalSubmissionWorkflow(provider, consultation, schemaCheck,
                    attempts, authorized, new FiscalRejectionRecorder(null), new NoopTransactions());
        }
    }

    private static final class FakeAttempts extends FiscalSubmissionAttemptService {
        final AtomicInteger reservations = new AtomicInteger();
        FiscalSubmissionAttempt attempt;
        FakeAttempts() { super(null, null); }
        @Override public FiscalSubmissionAttempt reserve(FiscalAuthorizationRequest request) {
            reservations.incrementAndGet();
            if (attempt == null) {
                var document = new FiscalDocument(new Command(1));
                ReflectionTestUtils.setField(document, "id", request.documentId());
                attempt = new FiscalSubmissionAttempt(document, request.accessKey(), "0".repeat(64));
                ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
            }
            return attempt;
        }
        @Override public FiscalSubmissionAttempt beginDispatch(UUID id) { attempt.dispatch(); return attempt; }
        @Override public FiscalSubmissionAttempt markUncertain(UUID id) { attempt.uncertain(); return attempt; }
        @Override public FiscalSubmissionAttempt findMatching(FiscalAuthorizationRequest req) { return attempt; }
        @Override public FiscalSubmissionAttempt resolve(UUID id) {
            if (attempt.getStatus() == FiscalSubmissionAttemptStatus.UNCERTAIN) {
                throw new IllegalStateException("no recorded outcome");
            }
            attempt.resolve();
            return attempt;
        }
    }

    private static final class NoopTransactions implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
