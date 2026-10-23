package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalCancellationPreparationServiceTests {
    private static final String KEY = "35261011222333000181650010000000011123456789";
    private static final String PROTOCOL = "135260000000001";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);
    private static KeyPair keyPair;
    private static X509Certificate certificate;

    @BeforeAll
    static void credentials() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        certificate = NfceXmlSignerTests.selfSignedTestCertificate(keyPair);
    }

    @Test
    void preparesSignedEventFromAuthorizedDocumentWithoutSendingOrSaving() {
        UUID id = UUID.randomUUID();
        FiscalDocument document = authorized();
        AtomicInteger reads = new AtomicInteger();
        FiscalDocumentRepository repository = repository(id, document, reads);
        var service = service(repository);

        FiscalCancellationRequest request = service.prepare(id,
                "Venda desfeita antes da entrega", false, credentialsValue());

        assertEquals(id, request.documentId());
        assertEquals(KEY, request.accessKey());
        assertTrue(request.signedEventXml().contains("<nProt>" + PROTOCOL + "</nProt>"));
        assertTrue(request.signedEventXml().contains("<tpAmb>2</tpAmb>"));
        assertEquals(FiscalDocumentStatus.AUTHORIZED, document.getStatus());
        assertEquals(1, reads.get());
    }

    @Test
    void refusesUnreadyDocumentAndDoesNotSign() {
        UUID id = UUID.randomUUID();
        FiscalDocument document = authorized();
        AtomicInteger reads = new AtomicInteger();
        FiscalDocumentRepository repository = repository(id, document, reads);
        var service = service(repository);
        document.setStatus(FiscalDocumentStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> service.prepare(id,
                "Venda desfeita antes da entrega", false, credentialsValue()));
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setProtocolNumber(null);
        assertThrows(IllegalStateException.class, () -> service.prepare(id,
                "Venda desfeita antes da entrega", false, credentialsValue()));
        document.setProtocolNumber(PROTOCOL);
        document.setAccessKey("0".repeat(44));
        assertThrows(IllegalStateException.class, () -> service.prepare(id,
                "Venda desfeita antes da entrega", false, credentialsValue()));
        assertEquals(3, reads.get());
    }

    @Test
    void refusesLateOrMerchandiseLeft() {
        UUID id = UUID.randomUUID();
        FiscalDocument document = authorized();
        FiscalDocumentRepository repository = repository(id, document, new AtomicInteger());
        var service = service(repository);
        assertThrows(IllegalArgumentException.class, () -> service.prepare(id,
                "Venda desfeita antes da entrega", true, credentialsValue()));
        document.setAuthorizedAt(OffsetDateTime.parse("2026-10-21T14:59:59Z"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(id,
                "Venda desfeita antes da entrega", false, credentialsValue()));
    }

    private static FiscalCancellationPreparationService service(FiscalDocumentRepository repository) {
        return new FiscalCancellationPreparationService(
                repository, new NfceCancellationEventSigner(), CLOCK);
    }

    private static FiscalDocumentRepository repository(UUID id, FiscalDocument document, AtomicInteger reads) {
        return (FiscalDocumentRepository) Proxy.newProxyInstance(
                FiscalDocumentRepository.class.getClassLoader(),
                new Class<?>[]{FiscalDocumentRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        assertEquals(id, args[0]);
                        reads.incrementAndGet();
                        return Optional.of(document);
                    }
                    throw new AssertionError("unexpected repository operation: " + method.getName());
                });
    }

    private static Pkcs12SigningCredentials credentialsValue() {
        return new Pkcs12SigningCredentials(keyPair.getPrivate(), certificate);
    }

    private static FiscalDocument authorized() {
        FiscalDocument document = new FiscalDocument(new Command(1));
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setAccessKey(KEY);
        document.setProtocolNumber(PROTOCOL);
        document.setXmlContent("<nfeProc>authorized original</nfeProc>");
        document.setAuthorizedAt(OffsetDateTime.parse("2026-10-21T15:05:00Z"));
        return document;
    }
}
