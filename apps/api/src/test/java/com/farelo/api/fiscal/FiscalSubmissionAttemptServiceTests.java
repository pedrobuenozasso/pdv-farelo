package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalSubmissionAttemptServiceTests {
    private static final String KEY = "35261011222333000181650010000000011123456789";

    @Test
    void reservationIsIdempotentOnlyForTheSameDocumentKeyAndPayload() {
        Fixture fixture = new Fixture();
        var request = new FiscalAuthorizationRequest(fixture.documentId, KEY, "<signed>one</signed>");

        FiscalSubmissionAttempt first = fixture.service.reserve(request);
        FiscalSubmissionAttempt second = fixture.service.reserve(request);

        assertSame(first, second);
        assertEquals(FiscalSubmissionAttemptStatus.RESERVED, first.getStatus());
        assertEquals(1, fixture.inserts.get());
        fixture.document.setXmlContent("<signed>changed</signed>");
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(
                new FiscalAuthorizationRequest(fixture.documentId, KEY, "<signed>changed</signed>")));
        fixture.document.setAccessKey("0".repeat(44));
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(request));
    }

    @Test
    void dispatchIsOneShotAndUncertaintyCannotBeRetriedOrResolvedWithoutEvidence() {
        Fixture fixture = new Fixture();
        FiscalSubmissionAttempt attempt = fixture.service.reserve(
                new FiscalAuthorizationRequest(fixture.documentId, KEY, "<signed>one</signed>"));

        fixture.service.beginDispatch(attempt.getId());
        assertEquals(FiscalSubmissionAttemptStatus.DISPATCHING, attempt.getStatus());
        assertThrows(IllegalStateException.class, () -> fixture.service.beginDispatch(attempt.getId()));
        assertThrows(IllegalStateException.class, () -> fixture.service.resolve(attempt.getId()));
        fixture.service.markUncertain(attempt.getId());
        assertEquals(FiscalSubmissionAttemptStatus.UNCERTAIN, attempt.getStatus());
        assertThrows(IllegalStateException.class, () -> fixture.service.beginDispatch(attempt.getId()));
        assertThrows(IllegalStateException.class, () -> fixture.service.markUncertain(attempt.getId()));
        fixture.document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        fixture.service.resolve(attempt.getId());
        assertEquals(FiscalSubmissionAttemptStatus.RESOLVED, attempt.getStatus());
        assertThrows(IllegalStateException.class, () -> fixture.service.beginDispatch(attempt.getId()));
    }

    @Test
    void cannotDispatchIfFiscalDocumentChangedBeforeNetworkStep() {
        Fixture fixture = new Fixture();
        FiscalSubmissionAttempt attempt = fixture.service.reserve(
                new FiscalAuthorizationRequest(fixture.documentId, KEY, "<signed>one</signed>"));
        fixture.document.setStatus(FiscalDocumentStatus.REJECTED);
        assertThrows(IllegalStateException.class, () -> fixture.service.beginDispatch(attempt.getId()));
        assertEquals(FiscalSubmissionAttemptStatus.RESERVED, attempt.getStatus());
    }

    @Test
    void refusesReservationWhenSignedXmlHasNotBeenPersistedAndDispatchIfItChanges() {
        Fixture fixture = new Fixture();
        var request = new FiscalAuthorizationRequest(fixture.documentId, KEY, "<signed>one</signed>");
        fixture.document.setXmlContent(null);
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(request));
        assertEquals(0, fixture.inserts.get());
        fixture.document.setXmlContent(request.signedXml());
        FiscalSubmissionAttempt attempt = fixture.service.reserve(request);
        fixture.document.setXmlContent("<signed>other</signed>");
        assertThrows(IllegalStateException.class, () -> fixture.service.beginDispatch(attempt.getId()));
        assertEquals(FiscalSubmissionAttemptStatus.RESERVED, attempt.getStatus());
    }

    private static final class Fixture {
        final UUID documentId = UUID.randomUUID();
        final FiscalDocument document = new FiscalDocument(new Command(1));
        final AtomicReference<FiscalSubmissionAttempt> attempt = new AtomicReference<>();
        final AtomicInteger inserts = new AtomicInteger();
        final FiscalSubmissionAttemptService service;

        Fixture() {
            ReflectionTestUtils.setField(document, "id", documentId);
            document.setStatus(FiscalDocumentStatus.PROCESSING);
            document.setAccessKey(KEY);
            document.setXmlContent("<signed>one</signed>");
            FiscalDocumentRepository documents = (FiscalDocumentRepository) Proxy.newProxyInstance(
                    FiscalDocumentRepository.class.getClassLoader(),
                    new Class<?>[]{FiscalDocumentRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findByIdForUpdate")) {
                            assertEquals(documentId, args[0]);
                            return Optional.of(document);
                        }
                        throw new AssertionError("unexpected document operation: " + method.getName());
                    });
            FiscalSubmissionAttemptRepository attempts = (FiscalSubmissionAttemptRepository) Proxy.newProxyInstance(
                    FiscalSubmissionAttemptRepository.class.getClassLoader(),
                    new Class<?>[]{FiscalSubmissionAttemptRepository.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "findByDocumentId" -> { return Optional.ofNullable(attempt.get()); }
                            case "findDocumentIdById" -> {
                                var current = attempt.get();
                                return current != null && current.getId().equals(args[0])
                                        ? Optional.of(documentId) : Optional.empty();
                            }
                            case "findByIdForUpdate" -> {
                                var current = attempt.get();
                                return current != null && current.getId().equals(args[0])
                                        ? Optional.of(current) : Optional.empty();
                            }
                            case "saveAndFlush" -> {
                                FiscalSubmissionAttempt value = (FiscalSubmissionAttempt) args[0];
                                ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
                                attempt.set(value);
                                inserts.incrementAndGet();
                                return value;
                            }
                            case "save" -> { return args[0]; }
                            default -> throw new AssertionError("unexpected attempt operation: " + method.getName());
                        }
                    });
            service = new FiscalSubmissionAttemptService(attempts, documents);
        }
    }
}
