package com.farelo.api.fiscal;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Explicit, unregistered homologation workflow. A dispatch fence commits before
 * transport; an uncertain send is reconciled by key and is never sent again.
 */
public final class HomologationFiscalSubmissionWorkflow {
    public enum Outcome { AUTHORIZED, REJECTED, NOT_FOUND, UNRESOLVED, ALREADY_RESOLVED }

    private final FiscalProvider provider;
    private final SefazSpProtocolConsultationClient consultation;
    private final Consumer<String> schemaValidation;
    private final FiscalSubmissionAttemptService attempts;
    private final FiscalAuthorizationRecorder authorizations;
    private final FiscalRejectionRecorder rejections;
    private final TransactionTemplate transaction;

    public HomologationFiscalSubmissionWorkflow(SefazSpNfceProvider provider,
            SefazSpProtocolConsultationClient consultation, NfceSchemaValidator validator,
            FiscalSubmissionAttemptService attempts, FiscalAuthorizationRecorder authorizations,
            FiscalRejectionRecorder rejections, PlatformTransactionManager manager) {
        this(provider, consultation, Objects.requireNonNull(validator, "validator")::validate,
                attempts, authorizations, rejections, manager);
    }

    // Package scope permits transport/schema fakes; production requires concrete SP adapters and XSD.
    HomologationFiscalSubmissionWorkflow(FiscalProvider provider,
            SefazSpProtocolConsultationClient consultation, Consumer<String> schemaValidation,
            FiscalSubmissionAttemptService attempts, FiscalAuthorizationRecorder authorizations,
            FiscalRejectionRecorder rejections, PlatformTransactionManager manager) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.consultation = Objects.requireNonNull(consultation, "consultation");
        this.schemaValidation = Objects.requireNonNull(schemaValidation, "schemaValidation");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.authorizations = Objects.requireNonNull(authorizations, "authorizations");
        this.rejections = Objects.requireNonNull(rejections, "rejections");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(manager, "manager"));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Outcome submitOnce(FiscalAuthorizationRequest request) {
        outsideTransaction();
        Objects.requireNonNull(request, "request");
        // Both preflight checks must pass before reserving a number or crossing the network fence.
        SefazSpNfceProvider.envelope(request);
        schemaValidation.accept(request.signedXml());
        FiscalSubmissionAttempt attempt = committed(() -> attempts.reserve(request));
        if (attempt.getStatus() != FiscalSubmissionAttemptStatus.RESERVED) {
            return reconcileExisting(request, attempt);
        }
        UUID id = attempt.getId();
        committed(() -> attempts.beginDispatch(id));
        FiscalAuthorizationResponse response;
        try {
            response = provider.authorize(request);
        } catch (RuntimeException ex) {
            committed(() -> attempts.markUncertain(id));
            return consultUncertain(request, id);
        }

        // Classify the reply before changing either persisted state. Only a
        // definite per-note result may close the attempt.
        boolean authorized;
        try {
            SefazSpAuthorizationParser.parse(request, response);
            authorized = true;
        } catch (IllegalArgumentException notAuthorized) {
            authorized = false;
        }
        if (authorized) {
            committed(() -> {
                authorizations.record(request, response);
                attempts.resolve(id);
                return null;
            });
            return Outcome.AUTHORIZED;
        }
        boolean rejected;
        try {
            SefazSpRejectionParser.parse(request, response);
            rejected = true;
        } catch (IllegalArgumentException notRejected) {
            rejected = false;
        }
        if (rejected) {
            committed(() -> {
                rejections.record(request, response);
                attempts.resolve(id);
                return null;
            });
            return Outcome.REJECTED;
        }
        committed(() -> attempts.markUncertain(id));
        return consultUncertain(request, id);
    }

    /** Recovers an earlier dispatch without making another authorization request. */
    public Outcome reconcileOnly(FiscalAuthorizationRequest request) {
        outsideTransaction();
        Objects.requireNonNull(request, "request");
        return reconcileExisting(request, committed(() -> attempts.findMatching(request)));
    }

    private Outcome reconcileExisting(FiscalAuthorizationRequest request, FiscalSubmissionAttempt attempt) {
        if (attempt.getStatus() == FiscalSubmissionAttemptStatus.RESOLVED) return Outcome.ALREADY_RESOLVED;
        if (attempt.getStatus() == FiscalSubmissionAttemptStatus.RESERVED) {
            throw new IllegalStateException("submission has not crossed the dispatch fence");
        }
        // A crash may have occurred after recording the outcome but before
        // closing the fence. Resolve that durable outcome before consulting.
        try {
            committed(() -> attempts.resolve(attempt.getId()));
            return Outcome.ALREADY_RESOLVED;
        } catch (IllegalStateException notRecorded) {
            // The document is still pending. Continue with consultation only.
        }
        if (attempt.getStatus() == FiscalSubmissionAttemptStatus.DISPATCHING) {
            committed(() -> attempts.markUncertain(attempt.getId()));
        }
        return consultUncertain(request, attempt.getId());
    }

    private Outcome consultUncertain(FiscalAuthorizationRequest request, UUID attemptId) {
        FiscalConsultationResponse reply;
        try {
            reply = consultation.consult(request.accessKey());
        } catch (FiscalConsultationUnavailableException ex) {
            return Outcome.UNRESOLVED;
        }
        try {
            FiscalConsultationResult result = committed(() -> {
                FiscalConsultationResult parsed = authorizations.reconcile(request, reply);
                if (parsed == FiscalConsultationResult.AUTHORIZED) attempts.resolve(attemptId);
                return parsed;
            });
            return switch (result) {
                case AUTHORIZED -> Outcome.AUTHORIZED;
                case NOT_FOUND -> Outcome.NOT_FOUND;
                case UNRESOLVED -> Outcome.UNRESOLVED;
            };
        } catch (IllegalArgumentException malformedReply) {
            return Outcome.UNRESOLVED;
        }
    }

    private <T> T committed(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("fiscal transport requires a committed dispatch fence");
        }
    }
}
