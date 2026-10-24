package com.farelo.api.fiscal;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Explicit preparation step for one closed comanda in SP homologation. It
 * persists a signed, QR-decorated XML and never contacts SEFAZ. Not a bean:
 * the caller must supply a reviewed XSD and signing credentials explicitly.
 */
public final class HomologationNfcePreparationService {
    public record Input(UUID documentId, NfceXmlBuilder.Issuer issuer, String operation,
                        int series, String randomCode, OffsetDateTime issuedAt) { }

    private final FiscalDocumentRepository documents;
    private final FiscalNumberReservationService numbering;
    private final NfceCommandDraftService drafts;
    private final NfceXmlSigner signer;
    private final NfceQrCodeV3 qrCode;
    private final Consumer<String> schemaValidation;
    private final TransactionTemplate transaction;

    public HomologationNfcePreparationService(FiscalDocumentRepository documents,
            FiscalNumberReservationService numbering, NfceCommandDraftService drafts,
            NfceSchemaValidator validator, PlatformTransactionManager manager) {
        this(documents, numbering, drafts, new NfceXmlSigner(), new NfceQrCodeV3(),
                Objects.requireNonNull(validator, "validator")::validate, manager);
    }

    /** Explicit operator path: the mounted, pinned package must pass integrity checks first. */
    public static HomologationNfcePreparationService usingMountedOfficialSchemas(
            FiscalDocumentRepository documents, FiscalNumberReservationService numbering,
            NfceCommandDraftService drafts, PlatformTransactionManager manager) {
        return new HomologationNfcePreparationService(documents, numbering, drafts,
                NfceOfficialSchemaPackage.loadMounted(), manager);
    }

    // Package scope allows deterministic, credential-free unit tests.
    HomologationNfcePreparationService(FiscalDocumentRepository documents,
            FiscalNumberReservationService numbering, NfceCommandDraftService drafts,
            NfceXmlSigner signer, NfceQrCodeV3 qrCode, Consumer<String> schemaValidation,
            PlatformTransactionManager manager) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.numbering = Objects.requireNonNull(numbering, "numbering");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.qrCode = Objects.requireNonNull(qrCode, "qrCode");
        this.schemaValidation = Objects.requireNonNull(schemaValidation, "schemaValidation");
        transaction = new TransactionTemplate(Objects.requireNonNull(manager, "manager"));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public FiscalAuthorizationRequest prepare(Input input, PrivateKey key, X509Certificate certificate) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("NFC-e preparation requires an independent transaction");
        }
        if (input == null || input.documentId() == null || input.issuer() == null
                || input.issuer().cnpj() == null || input.operation() == null
                || input.randomCode() == null || input.issuedAt() == null
                || key == null || certificate == null) {
            throw new IllegalArgumentException("explicit NFC-e data and A1 credentials are required");
        }
        // ICP-Brasil identifies the legal entity in SAN otherName OID 2.16.76.1.3.3.
        // Check before reserving a sequence value; signer verifies key/cert match later.
        IcpBrasilCertificateIdentity.requireIssuerCnpj(certificate, input.issuer().cnpj());
        return transaction.execute(status -> prepareInTransaction(input, key, certificate));
    }

    private FiscalAuthorizationRequest prepareInTransaction(Input input, PrivateKey key,
                                                            X509Certificate certificate) {
        FiscalDocument document = documents.findByIdForUpdate(input.documentId())
                .orElseThrow(() -> new FiscalDocumentNotFoundException(input.documentId()));
        if (document.getStatus() != FiscalDocumentStatus.PENDING || document.getCommand() == null
                || document.getCommand().getId() == null || document.getAccessKey() != null
                || document.getXmlContent() != null || document.getProtocolNumber() != null) {
            throw new IllegalStateException("fiscal document is not an unused placeholder");
        }
        document = numbering.reserve(input.documentId(), input.issuer().cnpj(),
                FiscalNumberEnvironment.HOMOLOGATION, input.series());
        if (document.getFiscalEnvironment() != FiscalNumberEnvironment.HOMOLOGATION
                || !input.issuer().cnpj().equals(document.getIssuerCnpj())
                || document.getDocumentNumber() == null || document.getSeries() == null) {
            throw new IllegalStateException("fiscal numbering reservation does not match the issuer");
        }
        NfceXmlBuilder.Result draft = drafts.buildHomologation(document.getCommand().getId(),
                new NfceCommandDraftService.Header(input.issuer(), input.operation(),
                        document.getSeries(), document.getDocumentNumber(),
                        input.randomCode(), input.issuedAt()));
        String signed = signer.signHomologation(draft.unsignedXml(), key, certificate);
        String withQr = qrCode.addToSignedHomologation(signed);
        var request = new FiscalAuthorizationRequest(input.documentId(), draft.accessKey(), withQr);
        SefazSpNfceProvider.envelope(request);
        schemaValidation.accept(withQr);
        document.setAccessKey(draft.accessKey());
        document.setXmlContent(withQr);
        document.setStatus(FiscalDocumentStatus.PROCESSING);
        documents.saveAndFlush(document);
        return request;
    }
}
