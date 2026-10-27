package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HomologationCommandPreparationTests {
    @Test
    void rejectsAmbientTransactionBeforeCreatingPendingDocument() {
        var pending = mock(FiscalPendingCommandService.class);
        var preparer = mock(HomologationNfcePreparationService.class);
        var bridge = new HomologationCommandPreparation(pending, preparer);
        var issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", new NfceXmlBuilder.Address(
                "Rua Exemplo", "100", "Centro", "3550308", "Sao Paulo", "01001000"));
        var input = new HomologationCommandPreparation.Input(21, issuer, "VENDA", 1,
                "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> bridge.prepare(input, mock(PrivateKey.class),
                    mock(X509Certificate.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("independent transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(pending, preparer);
    }
}
