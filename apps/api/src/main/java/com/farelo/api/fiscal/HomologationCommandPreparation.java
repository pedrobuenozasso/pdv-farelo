package com.farelo.api.fiscal;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Explicit bridge from a paid, closed comanda to the existing homologation
 * preparer. Deliberately not a Spring bean or HTTP endpoint: an operator must
 * supply reviewed fiscal data, schemas and signing credentials out of band.
 * A failed preparation leaves only the reusable, unnumbered placeholder;
 * a prepared document cannot be started again or silently reissued.
 */
public final class HomologationCommandPreparation {
    public record Input(int commandNumber, NfceXmlBuilder.Issuer issuer, String operation,
                        int series, String randomCode, OffsetDateTime issuedAt) { }

    private final FiscalPendingCommandService pending;
    private final HomologationNfcePreparationService preparation;

    public HomologationCommandPreparation(FiscalPendingCommandService pending,
                                         HomologationNfcePreparationService preparation) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
    }

    public FiscalAuthorizationRequest prepare(Input input, PrivateKey key,
                                              X509Certificate certificate) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("NFC-e preparation requires an independent transaction");
        }
        if (input == null || input.issuer() == null || input.operation() == null
                || input.randomCode() == null || input.issuedAt() == null
                || key == null || certificate == null) {
            throw new IllegalArgumentException("explicit fiscal data and signing credentials are required");
        }
        FiscalDocument document = pending.start(input.commandNumber());
        return preparation.prepare(new HomologationNfcePreparationService.Input(
                document.getId(), input.issuer(), input.operation(), input.series(),
                input.randomCode(), input.issuedAt()), key, certificate);
    }
}
