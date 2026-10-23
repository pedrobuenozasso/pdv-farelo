package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway V69 and row-locked cursor on real PostgreSQL when Docker is available. */
@SpringBootTest
@Transactional
class FiscalNumberReservationIntegrationTests extends AbstractIntegrationTest {
    @Autowired private FiscalNumberSequenceRepository sequences;
    @Autowired private FiscalDocumentRepository documents;
    @Autowired private CompanyFiscalConfigurationRepository companies;
    @Autowired private CommandRepository commands;
    @Autowired private FiscalNumberReservationService reservations;

    @Test
    void reservesSequentiallyWithinOneExplicitlyProvisionedHomologationScope() {
        CompanyFiscalConfiguration company = companies.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> new CompanyFiscalConfiguration("11222333000181", "Farelo Teste"));
        String issuer = company.getCnpj();
        companies.saveAndFlush(company);
        sequences.saveAndFlush(new FiscalNumberSequence(
                issuer, FiscalNumberEnvironment.HOMOLOGATION, 901, 13800));
        UUID first = documents.saveAndFlush(new FiscalDocument(
                commands.findByNumber(21).orElseThrow())).getId();
        UUID second = documents.saveAndFlush(new FiscalDocument(
                commands.findByNumber(22).orElseThrow())).getId();

        assertThat(reservations.reserve(first, issuer, FiscalNumberEnvironment.HOMOLOGATION, 901)
                .getDocumentNumber()).isEqualTo(13800);
        assertThat(reservations.reserve(first, issuer, FiscalNumberEnvironment.HOMOLOGATION, 901)
                .getDocumentNumber()).isEqualTo(13800);
        assertThat(reservations.reserve(second, issuer, FiscalNumberEnvironment.HOMOLOGATION, 901)
                .getDocumentNumber()).isEqualTo(13801);
        assertThat(sequences.findScopeForUpdate(issuer, FiscalNumberEnvironment.HOMOLOGATION, 901))
                .isPresent().get().extracting(FiscalNumberSequence::getNextNumber).isEqualTo(13802L);
    }
}
