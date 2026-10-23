package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalNumberReservationServiceTests {
    private static final String CNPJ = "11222333000181";

    @Test
    void reservesInScopeAndRepeatingSameRequestDoesNotAdvanceCursor() {
        Fixture fixture = new Fixture(13800);
        UUID firstId = fixture.document();
        UUID secondId = fixture.document();

        FiscalDocument first = fixture.service.reserve(firstId, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1);
        assertEquals(13800, first.getDocumentNumber());
        assertEquals(FiscalNumberEnvironment.HOMOLOGATION, first.getFiscalEnvironment());
        assertEquals(13801, fixture.sequence.getNextNumber());
        assertEquals(first, fixture.service.reserve(firstId, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1));
        assertEquals(13801, fixture.sequence.getNextNumber());
        FiscalDocument second = fixture.service.reserve(secondId, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1);
        assertEquals(13801, second.getDocumentNumber());
        assertEquals(13802, fixture.sequence.getNextNumber());
    }

    @Test
    void refusesUnprovisionedScopeAndConflictingReservation() {
        Fixture fixture = new Fixture(13800);
        UUID id = fixture.document();
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(
                id, CNPJ, FiscalNumberEnvironment.PRODUCTION, 1));
        assertEquals(13800, fixture.sequence.getNextNumber());
        fixture.service.reserve(id, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1);
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(
                id, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 2));
        assertEquals(13801, fixture.sequence.getNextNumber());
    }

    @Test
    void refusesDocumentAlreadyIssuedOrPartiallyNumbered() {
        Fixture fixture = new Fixture(13800);
        UUID id = fixture.document();
        FiscalDocument document = fixture.rows.get(id);
        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(
                id, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1));
        document.setStatus(FiscalDocumentStatus.PENDING);
        document.setSeries(1);
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve(
                id, CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1));
        assertEquals(13800, fixture.sequence.getNextNumber());
    }

    @Test
    void stopsAtMaximumNineDigitNumber() {
        FiscalNumberSequence sequence = new FiscalNumberSequence(
                CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1, 999_999_999);
        assertEquals(999_999_999, sequence.takeNext());
        assertThrows(IllegalStateException.class, sequence::takeNext);
    }

    private static final class Fixture {
        final Map<UUID, FiscalDocument> rows = new HashMap<>();
        final FiscalNumberSequence sequence;
        final FiscalNumberReservationService service;

        Fixture(int start) {
            sequence = new FiscalNumberSequence(CNPJ, FiscalNumberEnvironment.HOMOLOGATION, 1, start);
            FiscalDocumentRepository docs = (FiscalDocumentRepository) Proxy.newProxyInstance(
                    FiscalDocumentRepository.class.getClassLoader(),
                    new Class<?>[]{FiscalDocumentRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdForUpdate" -> Optional.ofNullable(rows.get(args[0]));
                        case "saveAndFlush" -> args[0];
                        default -> throw new AssertionError("unexpected document operation: " + method.getName());
                    });
            FiscalNumberSequenceRepository sequences = (FiscalNumberSequenceRepository) Proxy.newProxyInstance(
                    FiscalNumberSequenceRepository.class.getClassLoader(),
                    new Class<?>[]{FiscalNumberSequenceRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findScopeForUpdate" -> CNPJ.equals(args[0])
                                && args[1] == FiscalNumberEnvironment.HOMOLOGATION
                                && ((int) args[2]) == 1 ? Optional.of(sequence) : Optional.empty();
                        case "save" -> args[0];
                        default -> throw new AssertionError("unexpected sequence operation: " + method.getName());
                    });
            CompanyFiscalConfiguration company = new CompanyFiscalConfiguration(CNPJ, "Farelo de Bolo");
            CompanyFiscalConfigurationRepository companies = (CompanyFiscalConfigurationRepository)
                    Proxy.newProxyInstance(CompanyFiscalConfigurationRepository.class.getClassLoader(),
                            new Class<?>[]{CompanyFiscalConfigurationRepository.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("findFirstByOrderByCreatedAtAsc")) {
                                    return Optional.of(company);
                                }
                                throw new AssertionError("unexpected company operation: " + method.getName());
                            });
            service = new FiscalNumberReservationService(docs, sequences, companies);
        }

        UUID document() {
            UUID id = UUID.randomUUID();
            FiscalDocument document = new FiscalDocument(new Command(1));
            ReflectionTestUtils.setField(document, "id", id);
            rows.put(id, document);
            return id;
        }
    }
}
