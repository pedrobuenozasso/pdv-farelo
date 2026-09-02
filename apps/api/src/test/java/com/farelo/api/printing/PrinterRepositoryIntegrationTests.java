package com.farelo.api.printing;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Printer} maps correctly onto the table created by
 * {@code V13__create_printer_table.sql}, against a real PostgreSQL instance.
 */
@SpringBootTest
class PrinterRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private PrinterRepository printerRepository;

    @Test
    void savesAndFindsPrinter() {
        Printer printer = new Printer("Impressora Bar");

        Printer saved = printerRepository.saveAndFlush(printer);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Printer> found = printerRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Impressora Bar");
        assertThat(found.get().isActive()).isTrue();
    }

}
