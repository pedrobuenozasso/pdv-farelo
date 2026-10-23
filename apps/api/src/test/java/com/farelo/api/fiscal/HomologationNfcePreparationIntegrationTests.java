package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs on PostgreSQL/Testcontainers when Docker is available. */
@SpringBootTest
class HomologationNfcePreparationIntegrationTests extends AbstractIntegrationTest {
    @Autowired FiscalDocumentRepository documents;
    @Autowired FiscalNumberSequenceRepository sequences;
    @Autowired CompanyFiscalConfigurationRepository companies;
    @Autowired CommandRepository commands;
    @Autowired FiscalNumberReservationService numbering;
    @Autowired PlatformTransactionManager manager;

    @Test
    void schemaFailureRollsBackNumberAndXmlThenDatabaseRejectsDuplicateIdentity() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager);
        Setup setup = tx.execute(status -> {
            var existing = companies.findFirstByOrderByCreatedAtAsc();
            var company = existing.orElseGet(() -> companies.saveAndFlush(
                    new CompanyFiscalConfiguration("11222333000181", "Farelo Teste")));
            int series = 993;
            long initial = 800_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),
                    50_000_000L);
            var sequence = sequences.saveAndFlush(new FiscalNumberSequence(company.getCnpj(),
                    FiscalNumberEnvironment.HOMOLOGATION, series, initial));
            var document = documents.saveAndFlush(new FiscalDocument(commands.findByNumber(21).orElseThrow()));
            return new Setup(document.getId(), sequence.getId(), company.getId(), existing.isEmpty(),
                    company.getCnpj(), series, initial);
        });
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var cert = TestIcpBrasilCertificate.issue(pair, setup.cnpj());
            var issuer = new NfceXmlBuilder.Issuer(setup.cnpj(), "Empresa Exemplo LTDA",
                    "123456789012", "1", new NfceXmlBuilder.Address(
                    "Rua Exemplo", "100", "Centro", "3550308", "Sao Paulo", "01001000"));
            var input = new HomologationNfcePreparationService.Input(setup.documentId(), issuer,
                    "VENDA", setup.series(), "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"));
            NfceCommandDraftService drafts = new NfceCommandDraftService(null, null, null, null, null) {
                @Override public NfceXmlBuilder.Result buildHomologation(UUID commandId, Header header) {
                    var zero = new NfceXmlBuilder.OtherContribution(BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO);
                    var item = new NfceXmlBuilder.Item("P1", "Cafe", "SEM GTIN", "19059090",
                            "0300100", "5102", "UN", new BigDecimal("1.0000"),
                            new BigDecimal("12.5000"), new BigDecimal("12.50"),
                            new NfceXmlBuilder.Taxes("0", "102", "99", "99", zero, zero));
                    return new NfceXmlBuilder().buildHomologation(new NfceXmlBuilder.Draft(
                            header.issuer(), header.operation(), header.series(), header.number(),
                            header.randomCode(), header.issuedAt(), List.of(item), new BigDecimal("12.50")));
                }
            };
            var invalid = new HomologationNfcePreparationService(documents, numbering, drafts,
                    new NfceXmlSigner(), new NfceQrCodeV3(), xml -> {
                        throw new IllegalArgumentException("invalid XSD");
                    }, manager);
            assertThatThrownBy(() -> invalid.prepare(input, pair.getPrivate(), cert))
                    .isInstanceOf(IllegalArgumentException.class);
            tx.executeWithoutResult(status -> {
                assertThat(documents.findById(setup.documentId()).orElseThrow().getStatus())
                        .isEqualTo(FiscalDocumentStatus.PENDING);
                assertThat(documents.findById(setup.documentId()).orElseThrow().getXmlContent()).isNull();
                assertThat(sequences.findById(setup.sequenceId()).orElseThrow().getNextNumber())
                        .isEqualTo(setup.initial());
            });

            var valid = new HomologationNfcePreparationService(documents, numbering, drafts,
                    new NfceXmlSigner(), new NfceQrCodeV3(), xml -> { }, manager);
            var prepared = valid.prepare(input, pair.getPrivate(), cert);
            tx.executeWithoutResult(status -> {
                var persisted = documents.findById(setup.documentId()).orElseThrow();
                assertThat(persisted.getAccessKey()).isEqualTo(prepared.accessKey());
                assertThat(persisted.getXmlContent()).isEqualTo(prepared.signedXml());
                assertThat(persisted.getDocumentNumber()).isEqualTo((int) setup.initial());
                assertThat(sequences.findById(setup.sequenceId()).orElseThrow().getNextNumber())
                        .isEqualTo(setup.initial() + 1);
            });

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                var duplicate = new FiscalDocument(commands.findByNumber(22).orElseThrow());
                duplicate.setIssuerCnpj(setup.cnpj());
                duplicate.setFiscalEnvironment(FiscalNumberEnvironment.HOMOLOGATION);
                duplicate.setSeries(setup.series());
                duplicate.setDocumentNumber((int) setup.initial());
                documents.saveAndFlush(duplicate);
            })).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            tx.executeWithoutResult(status -> {
                documents.deleteById(setup.documentId());
                sequences.deleteById(setup.sequenceId());
                if (setup.createdCompany()) companies.deleteById(setup.companyId());
            });
        }
    }

    private record Setup(UUID documentId, UUID sequenceId, UUID companyId,
                         boolean createdCompany, String cnpj, int series, long initial) { }
}
