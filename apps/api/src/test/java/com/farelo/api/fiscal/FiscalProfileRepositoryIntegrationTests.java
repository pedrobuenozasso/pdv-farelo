package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link FiscalProfile} maps correctly onto the table created
 * by {@code V27__create_fiscal_profile_table.sql}, against a real
 * PostgreSQL instance.
 */
@SpringBootTest
class FiscalProfileRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private FiscalProfileRepository fiscalProfileRepository;

    @Test
    void savesAndFindsFiscalProfile() {
        FiscalProfile fiscalProfile = new FiscalProfile("Isento");
        fiscalProfile.setDescription("Produtos sem incidência de ICMS");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Isento");
        assertThat(found.get().getDescription()).isEqualTo("Produtos sem incidência de ICMS");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void savesFiscalProfileWithoutDescription() {
        FiscalProfile fiscalProfile = new FiscalProfile("Tributado padrão");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isNull();
    }

}
