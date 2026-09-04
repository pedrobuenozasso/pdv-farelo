package com.farelo.api.fiscal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code get}/{@code save} only — no {@code create}/{@code listAll}/{@code
 * getById} like {@code FiscalProfileService}, matching {@code
 * CompanyFiscalConfigurationController}'s {@code GET}/{@code PUT}-only
 * shape (see that class's javadoc for the full singleton-endpoint
 * reasoning).
 */
@Service
public class CompanyFiscalConfigurationService {

    private final CompanyFiscalConfigurationRepository companyFiscalConfigurationRepository;

    public CompanyFiscalConfigurationService(CompanyFiscalConfigurationRepository companyFiscalConfigurationRepository) {
        this.companyFiscalConfigurationRepository = companyFiscalConfigurationRepository;
    }

    public CompanyFiscalConfiguration get() {
        return companyFiscalConfigurationRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(CompanyFiscalConfigurationNotFoundException::new);
    }

    /**
     * Creates the single row on the first call, replaces it (full
     * {@code PUT} semantics) on every subsequent call — the "at most one
     * row" invariant is kept here, in application code, rather than by a
     * database constraint. See {@link CompanyFiscalConfiguration}'s javadoc
     * for why.
     */
    @Transactional
    public CompanyFiscalConfiguration save(String cnpj, String legalName, String tradeName,
                                            String stateRegistration, String address) {
        CompanyFiscalConfiguration companyFiscalConfiguration = companyFiscalConfigurationRepository
                .findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> new CompanyFiscalConfiguration(cnpj, legalName));

        companyFiscalConfiguration.setCnpj(cnpj);
        companyFiscalConfiguration.setLegalName(legalName);
        companyFiscalConfiguration.setTradeName(tradeName);
        companyFiscalConfiguration.setStateRegistration(stateRegistration);
        companyFiscalConfiguration.setAddress(address);

        return companyFiscalConfigurationRepository.save(companyFiscalConfiguration);
    }

}
