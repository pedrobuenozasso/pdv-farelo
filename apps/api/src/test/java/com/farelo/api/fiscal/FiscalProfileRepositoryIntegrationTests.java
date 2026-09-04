package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // FARELO-152.
    @Test
    void savesAndFindsFiscalProfileWithNcm() {
        FiscalProfile fiscalProfile = new FiscalProfile("Tributado padrão");
        fiscalProfile.setNcm("12345678");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getNcm()).isEqualTo("12345678");
    }

    // FARELO-152.
    @Test
    void savesFiscalProfileWithoutNcm() {
        FiscalProfile fiscalProfile = new FiscalProfile("Isento");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getNcm()).isNull();
    }

    // FARELO-153.
    @Test
    void savesAndFindsFiscalProfileWithCfop() {
        FiscalProfile fiscalProfile = new FiscalProfile("Tributado padrão");
        fiscalProfile.setCfop("5102");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCfop()).isEqualTo("5102");
    }

    // FARELO-153.
    @Test
    void savesFiscalProfileWithoutCfop() {
        FiscalProfile fiscalProfile = new FiscalProfile("Isento");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCfop()).isNull();
    }

    // FARELO-154.
    @Test
    void savesAndFindsFiscalProfileWithCst() {
        FiscalProfile fiscalProfile = new FiscalProfile("Tributado padrão");
        fiscalProfile.setCst("60");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCst()).isEqualTo("60");
        assertThat(found.get().getCsosn()).isNull();
    }

    // FARELO-154.
    @Test
    void savesAndFindsFiscalProfileWithCsosn() {
        FiscalProfile fiscalProfile = new FiscalProfile("Simples Nacional");
        fiscalProfile.setCsosn("102");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCsosn()).isEqualTo("102");
        assertThat(found.get().getCst()).isNull();
    }

    // FARELO-154.
    @Test
    void savesFiscalProfileWithoutCstOrCsosn() {
        FiscalProfile fiscalProfile = new FiscalProfile("Isento");

        FiscalProfile saved = fiscalProfileRepository.saveAndFlush(fiscalProfile);

        Optional<FiscalProfile> found = fiscalProfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCst()).isNull();
        assertThat(found.get().getCsosn()).isNull();
    }

    // FARELO-154: ck_fiscal_profile_cst_csosn_exclusive (V33) rejects both
    // being set at once, even via a direct repository save that bypasses
    // the request DTO's own CstCsosnMutuallyExclusive check — same
    // defense-in-depth intent as the NCM/CFOP format CHECKs.
    @Test
    void rejectsFiscalProfileWithBothCstAndCsosnSet() {
        FiscalProfile fiscalProfile = new FiscalProfile("Inválido");
        fiscalProfile.setCst("60");
        fiscalProfile.setCsosn("102");

        assertThatThrownBy(() -> fiscalProfileRepository.saveAndFlush(fiscalProfile))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
