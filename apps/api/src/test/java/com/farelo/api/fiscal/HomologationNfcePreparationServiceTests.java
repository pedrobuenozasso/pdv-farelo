package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomologationNfcePreparationServiceTests {
    @Test
    void persistsSignedQrDocumentBeforeAnySubmission() throws Exception {
        Fixture fixture = new Fixture(xml -> { });
        var request = fixture.service.prepare(fixture.input, fixture.pair.getPrivate(), fixture.certificate);
        assertEquals(fixture.document.getId(), request.documentId());
        assertEquals(request.accessKey(), fixture.document.getAccessKey());
        assertEquals(request.signedXml(), fixture.document.getXmlContent());
        assertEquals(FiscalDocumentStatus.PROCESSING, fixture.document.getStatus());
        assertEquals(FiscalNumberEnvironment.HOMOLOGATION, fixture.document.getFiscalEnvironment());
        assertEquals(1, fixture.saves.get());
        assertTrue(request.signedXml().contains("|3|2"));
        assertThrows(IllegalStateException.class, () -> fixture.service.prepare(
                fixture.input, fixture.pair.getPrivate(), fixture.certificate));
        assertEquals(1, fixture.saves.get());
    }

    @Test
    void invalidSchemaPreventsDocumentPublication() throws Exception {
        Fixture fixture = new Fixture(xml -> { throw new IllegalArgumentException("invalid XSD"); });
        assertThrows(IllegalArgumentException.class, () -> fixture.service.prepare(
                fixture.input, fixture.pair.getPrivate(), fixture.certificate));
        assertEquals(FiscalDocumentStatus.PENDING, fixture.document.getStatus());
        assertEquals(0, fixture.saves.get());
    }

    @Test
    void missingSigningCredentialsFailBeforeNumberReservation() throws Exception {
        Fixture fixture = new Fixture(xml -> { });
        assertThrows(IllegalArgumentException.class, () -> fixture.service.prepare(fixture.input, null,
                fixture.certificate));
        assertEquals(0, fixture.reservations.get());
    }

    private static final class Fixture {
        final Command command = new Command(1);
        final FiscalDocument document = new FiscalDocument(command);
        final AtomicInteger reservations = new AtomicInteger();
        final AtomicInteger saves = new AtomicInteger();
        final java.security.KeyPair pair;
        final java.security.cert.X509Certificate certificate;
        final NfceXmlBuilder.Issuer issuer;
        final HomologationNfcePreparationService.Input input;
        final HomologationNfcePreparationService service;

        Fixture(Consumer<String> schemaCheck) throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            pair = generator.generateKeyPair();
            certificate = NfceXmlSignerTests.selfSignedTestCertificate(pair);
            ReflectionTestUtils.setField(command, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
            issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                    "123456789012", "1", new NfceXmlBuilder.Address(
                    "Rua Exemplo", "100", "Centro", "3550308", "Sao Paulo", "01001000"));
            input = new HomologationNfcePreparationService.Input(document.getId(), issuer,
                    "VENDA", 1, "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"));
            FiscalDocumentRepository repository = (FiscalDocumentRepository) Proxy.newProxyInstance(
                    FiscalDocumentRepository.class.getClassLoader(),
                    new Class<?>[]{FiscalDocumentRepository.class}, (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "findByIdForUpdate" -> Optional.of(document);
                            case "saveAndFlush" -> { saves.incrementAndGet(); yield args[0]; }
                            default -> throw new AssertionError("unexpected repository call: " + method.getName());
                        };
                    });
            FiscalNumberReservationService numbering = new FiscalNumberReservationService(null, null, null) {
                @Override public FiscalDocument reserve(UUID id, String cnpj,
                        FiscalNumberEnvironment environment, int series) {
                    reservations.incrementAndGet();
                    document.setDocumentNumber(123);
                    document.setSeries(series);
                    document.setIssuerCnpj(cnpj);
                    document.setFiscalEnvironment(environment);
                    return document;
                }
            };
            NfceCommandDraftService drafts = new NfceCommandDraftService(null, null, null, null, null) {
                @Override public NfceXmlBuilder.Result buildHomologation(UUID commandId, Header header) {
                    assertEquals(command.getId(), commandId);
                    var zero = new NfceXmlBuilder.OtherContribution(BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO);
                    var taxes = new NfceXmlBuilder.Taxes("0", "102", "99", "99", zero, zero);
                    var item = new NfceXmlBuilder.Item("P1", "Cafe", "SEM GTIN", "19059090",
                            "0300100", "5102", "UN", new BigDecimal("1.0000"),
                            new BigDecimal("12.5000"), new BigDecimal("12.50"), taxes);
                    return new NfceXmlBuilder().buildHomologation(new NfceXmlBuilder.Draft(
                            header.issuer(), header.operation(), header.series(), header.number(),
                            header.randomCode(), header.issuedAt(), List.of(item),
                            new BigDecimal("12.50")));
                }
            };
            service = new HomologationNfcePreparationService(repository, numbering, drafts,
                    new NfceXmlSigner(), new NfceQrCodeV3(), schemaCheck, new NoopTransactions());
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
