package com.farelo.api.fiscal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists an authorization-attempt fence; never sends to SEFAZ itself.
 * Callers must commit {@link #beginDispatch(UUID)} before network I/O. Once
 * DISPATCHING, an interrupted process must consult the key, not retry.
 */
@Service
public class FiscalSubmissionAttemptService {
    private final FiscalSubmissionAttemptRepository attempts;
    private final FiscalDocumentRepository documents;

    public FiscalSubmissionAttemptService(FiscalSubmissionAttemptRepository attempts,
                                          FiscalDocumentRepository documents) {
        this.attempts = attempts;
        this.documents = documents;
    }

    @Transactional
    public FiscalSubmissionAttempt reserve(FiscalAuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        FiscalDocument document = documents.findByIdForUpdate(request.documentId())
                .orElseThrow(() -> new FiscalDocumentNotFoundException(request.documentId()));
        if (document.getStatus() != FiscalDocumentStatus.PROCESSING
                && document.getStatus() != FiscalDocumentStatus.CONTINGENCY
                || document.getFiscalEnvironment() != FiscalNumberEnvironment.HOMOLOGATION
                || !request.accessKey().equals(document.getAccessKey())
                || !request.signedXml().equals(document.getXmlContent())) {
            throw new IllegalStateException("fiscal document is not ready for submission");
        }
        String hash = sha256(request.signedXml());
        var existing = attempts.findByDocumentId(request.documentId());
        if (existing.isPresent()) {
            FiscalSubmissionAttempt attempt = existing.get();
            if (!attempt.getAccessKey().equals(request.accessKey())
                    || !attempt.getRequestSha256().equals(hash)) {
                throw new IllegalStateException("fiscal submission payload differs from reserved attempt");
            }
            return attempt;
        }
        return attempts.saveAndFlush(new FiscalSubmissionAttempt(document, request.accessKey(), hash));
    }

    @Transactional
    public FiscalSubmissionAttempt beginDispatch(UUID attemptId) {
        Locked pair = lockDocumentThenAttempt(attemptId);
        FiscalSubmissionAttempt attempt = pair.attempt();
        FiscalDocument document = pair.document();
        if (document.getStatus() != FiscalDocumentStatus.PROCESSING
                && document.getStatus() != FiscalDocumentStatus.CONTINGENCY
                || document.getFiscalEnvironment() != FiscalNumberEnvironment.HOMOLOGATION
                || !attempt.getAccessKey().equals(document.getAccessKey())
                || document.getXmlContent() == null
                || !attempt.getRequestSha256().equals(sha256(document.getXmlContent()))) {
            throw new IllegalStateException("fiscal document is not ready for dispatch");
        }
        attempt.dispatch();
        return attempts.save(attempt);
    }

    @Transactional
    public FiscalSubmissionAttempt markUncertain(UUID attemptId) {
        FiscalSubmissionAttempt attempt = locked(attemptId);
        attempt.uncertain();
        return attempts.save(attempt);
    }

    /** Reads a previously fenced payload even after its document was resolved. */
    @Transactional(readOnly = true)
    public FiscalSubmissionAttempt findMatching(FiscalAuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        FiscalSubmissionAttempt attempt = attempts.findByDocumentId(request.documentId())
                .orElseThrow(() -> new IllegalArgumentException("fiscal submission attempt not found"));
        if (!attempt.getAccessKey().equals(request.accessKey())
                || !attempt.getRequestSha256().equals(sha256(request.signedXml()))) {
            throw new IllegalStateException("fiscal submission payload differs from reserved attempt");
        }
        return attempt;
    }

    /** Only closes the fence after an independent recorder persisted SEFAZ's outcome. */
    @Transactional
    public FiscalSubmissionAttempt resolve(UUID attemptId) {
        Locked pair = lockDocumentThenAttempt(attemptId);
        FiscalSubmissionAttempt attempt = pair.attempt();
        FiscalDocument document = pair.document();
        if (!attempt.getAccessKey().equals(document.getAccessKey())
                || document.getStatus() != FiscalDocumentStatus.AUTHORIZED
                    && document.getStatus() != FiscalDocumentStatus.REJECTED) {
            throw new IllegalStateException("SEFAZ outcome has not been persisted");
        }
        attempt.resolve();
        return attempts.save(attempt);
    }

    private FiscalSubmissionAttempt locked(UUID attemptId) {
        Objects.requireNonNull(attemptId, "attemptId");
        return attempts.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("fiscal submission attempt not found"));
    }

    private Locked lockDocumentThenAttempt(UUID attemptId) {
        Objects.requireNonNull(attemptId, "attemptId");
        UUID documentId = attempts.findDocumentIdById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("fiscal submission attempt not found"));
        FiscalDocument document = documents.findByIdForUpdate(documentId)
                .orElseThrow(() -> new FiscalDocumentNotFoundException(documentId));
        FiscalSubmissionAttempt attempt = locked(attemptId);
        if (!documentId.equals(attempt.getDocument().getId())) {
            throw new IllegalStateException("fiscal submission document changed");
        }
        return new Locked(document, attempt);
    }

    private record Locked(FiscalDocument document, FiscalSubmissionAttempt attempt) { }

    private static String sha256(String xml) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
