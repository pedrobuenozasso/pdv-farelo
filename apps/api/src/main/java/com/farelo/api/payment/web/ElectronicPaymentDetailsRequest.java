package com.farelo.api.payment.web;

import com.farelo.api.payment.ElectronicPaymentDetails;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fiscal transaction evidence copied from the actual PSP/terminal receipt. */
public record ElectronicPaymentDetailsRequest(
        @NotNull @Pattern(regexp = "[12]") String integrationType,
        @Pattern(regexp = "[0-9]{14}") String institutionCnpj,
        @Pattern(regexp = "[0-9]{2}") String cardBrand,
        @Size(max = 128) String authorizationCode,
        @Pattern(regexp = "[0-9]{14}") String receiverCnpj,
        @Size(max = 40) String terminalId) {

    @AssertTrue(message = "pagamento integrado exige CNPJ da instituição e código de autorização")
    public boolean isIntegratedEvidenceComplete() {
        return !"1".equals(integrationType) ||
                (institutionCnpj != null && authorizationCode != null && !authorizationCode.isBlank());
    }

    public ElectronicPaymentDetails toDomain() {
        return new ElectronicPaymentDetails(integrationType, institutionCnpj, cardBrand,
                authorizationCode, receiverCnpj, terminalId);
    }
}
