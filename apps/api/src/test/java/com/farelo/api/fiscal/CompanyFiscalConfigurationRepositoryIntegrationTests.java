package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link CompanyFiscalConfiguration} maps correctly onto the
 * table created by {@code V29__create_company_fiscal_configuration_table.sql},
 * against a real PostgreSQL instance, and that {@link
 * CompanyFiscalConfigurationRepository#findFirstByOrderByCreatedAtAsc()}
 * behaves as {@link CompanyFiscalConfigurationService} needs.
 *
 * <p>Nothing else in the schema FKs into {@code
 * company_fiscal_configuration} (unlike {@code fiscal_profile}, see {@code
 * FiscalProfileControllerIntegrationTests}'s javadoc for that landmine), so
 * a plain {@code deleteAll()} in {@code @BeforeEach} is safe here.
 */
@SpringBootTest
class CompanyFiscalConfigurationRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CompanyFiscalConfigurationRepository companyFiscalConfigurationRepository;

    @BeforeEach
    void cleanTable() {
        companyFiscalConfigurationRepository.deleteAll();
    }

    @Test
    void savesAndFindsCompanyFiscalConfiguration() {
        CompanyFiscalConfiguration companyFiscalConfiguration =
                new CompanyFiscalConfiguration("12.345.678/0001-90", "Farelo Comércio de Alimentos LTDA");
        companyFiscalConfiguration.setTradeName("Farelo Café");
        companyFiscalConfiguration.setStateRegistration("123.456.789.112");
        companyFiscalConfiguration.setAddress("Rua das Flores, 123 - São Paulo/SP");

        CompanyFiscalConfiguration saved = companyFiscalConfigurationRepository.saveAndFlush(companyFiscalConfiguration);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<CompanyFiscalConfiguration> found = companyFiscalConfigurationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(found.get().getLegalName()).isEqualTo("Farelo Comércio de Alimentos LTDA");
        assertThat(found.get().getTradeName()).isEqualTo("Farelo Café");
        assertThat(found.get().getStateRegistration()).isEqualTo("123.456.789.112");
        assertThat(found.get().getAddress()).isEqualTo("Rua das Flores, 123 - São Paulo/SP");
    }

    @Test
    void savesCompanyFiscalConfigurationWithoutOptionalFields() {
        CompanyFiscalConfiguration companyFiscalConfiguration =
                new CompanyFiscalConfiguration("12.345.678/0001-90", "Farelo Comércio de Alimentos LTDA");

        CompanyFiscalConfiguration saved = companyFiscalConfigurationRepository.saveAndFlush(companyFiscalConfiguration);

        Optional<CompanyFiscalConfiguration> found = companyFiscalConfigurationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTradeName()).isNull();
        assertThat(found.get().getStateRegistration()).isNull();
        assertThat(found.get().getAddress()).isNull();
    }

    @Test
    void findFirstByOrderByCreatedAtAscReturnsEmptyWhenNoRowExists() {
        assertThat(companyFiscalConfigurationRepository.findFirstByOrderByCreatedAtAsc()).isEmpty();
    }

    @Test
    void findFirstByOrderByCreatedAtAscReturnsTheOnlyRow() {
        CompanyFiscalConfiguration saved = companyFiscalConfigurationRepository.saveAndFlush(
                new CompanyFiscalConfiguration("12.345.678/0001-90", "Farelo Comércio de Alimentos LTDA"));

        Optional<CompanyFiscalConfiguration> found = companyFiscalConfigurationRepository.findFirstByOrderByCreatedAtAsc();

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

}
