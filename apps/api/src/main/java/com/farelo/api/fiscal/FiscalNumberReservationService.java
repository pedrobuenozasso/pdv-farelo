package com.farelo.api.fiscal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Atomically reserves one NFC-e series/number inside a pre-provisioned scope.
 * No controller or sale flow calls this service yet. Sequence rows are never
 * seeded automatically; a verified starting number is a launch prerequisite.
 */
@Service
public class FiscalNumberReservationService {
    private final FiscalDocumentRepository documents;
    private final FiscalNumberSequenceRepository sequences;
    private final CompanyFiscalConfigurationRepository company;

    public FiscalNumberReservationService(FiscalDocumentRepository documents,
                                          FiscalNumberSequenceRepository sequences,
                                          CompanyFiscalConfigurationRepository company) {
        this.documents = documents;
        this.sequences = sequences;
        this.company = company;
    }

    @Transactional
    public FiscalDocument reserve(UUID documentId, String issuerCnpj,
                                  FiscalNumberEnvironment environment, int series) {
        Objects.requireNonNull(documentId, "documentId");
        if (issuerCnpj == null || !issuerCnpj.matches("[0-9]{14}") || environment == null
                || series < 0 || series > 999) {
            throw new IllegalArgumentException("invalid NFC-e numbering scope");
        }
        FiscalDocument document = documents.findByIdForUpdate(documentId)
                .orElseThrow(() -> new FiscalDocumentNotFoundException(documentId));
        if (document.getDocumentNumber() != null || document.getSeries() != null
                || document.getIssuerCnpj() != null || document.getFiscalEnvironment() != null) {
            if (document.getDocumentNumber() != null
                    && document.getDocumentNumber() >= 1
                    && document.getDocumentNumber() <= 999_999_999
                    && document.getSeries() != null && document.getSeries() == series
                    && issuerCnpj.equals(document.getIssuerCnpj())
                    && environment == document.getFiscalEnvironment()) {
                return document; // same request: the sequence must not advance twice
            }
            throw new IllegalStateException("fiscal document has a different numbering reservation");
        }
        if (document.getStatus() != FiscalDocumentStatus.PENDING || document.getAccessKey() != null
                || document.getProtocolNumber() != null || document.getXmlContent() != null) {
            throw new IllegalStateException("fiscal document is not ready for numbering");
        }
        var configuration = company.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(CompanyFiscalConfigurationNotFoundException::new);
        if (!issuerCnpj.equals(configuration.getCnpj())) {
            throw new IllegalStateException("issuer differs from configured company");
        }
        FiscalNumberSequence sequence = sequences.findScopeForUpdate(issuerCnpj, environment, series)
                .orElseThrow(() -> new IllegalStateException("fiscal numbering scope is not provisioned"));
        int number = sequence.takeNext();
        document.setIssuerCnpj(issuerCnpj);
        document.setFiscalEnvironment(environment);
        document.setSeries(series);
        document.setDocumentNumber(number);
        sequences.save(sequence);
        return documents.saveAndFlush(document);
    }
}
