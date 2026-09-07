package com.farelo.api.cashier;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link Shift} maps correctly onto the table created by
 * {@code V39__create_shift_table.sql}, against a real PostgreSQL instance.
 */
@SpringBootTest
class ShiftRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private ShiftRepository shiftRepository;

    @AfterEach
    void cleanUp() {
        shiftRepository.deleteAll();
    }

    @Test
    void savesAndFindsShift() {
        Shift shift = new Shift("TURNO 01", "Caixa Dinheiro", "Tesouraria");

        Shift saved = shiftRepository.saveAndFlush(shift);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Shift> found = shiftRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("TURNO 01");
        assertThat(found.get().getOpeningAccountName()).isEqualTo("Caixa Dinheiro");
        assertThat(found.get().getClosingAccountName()).isEqualTo("Tesouraria");
    }

    @Test
    void findsByName() {
        shiftRepository.saveAndFlush(new Shift("TURNO 02", "Caixa Dinheiro", "Tesouraria"));

        Optional<Shift> found = shiftRepository.findByName("TURNO 02");

        assertThat(found).isPresent();
        assertThat(found.get().getOpeningAccountName()).isEqualTo("Caixa Dinheiro");
    }

    @Test
    void enforcesUniqueNameConstraint() {
        shiftRepository.saveAndFlush(new Shift("TURNO 03", "Caixa Dinheiro", "Tesouraria"));

        assertThatThrownBy(() -> shiftRepository.saveAndFlush(new Shift("TURNO 03", "Outra Conta", "Outra Tesouraria")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
