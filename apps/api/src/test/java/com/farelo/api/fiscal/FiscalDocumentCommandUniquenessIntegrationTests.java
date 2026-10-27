package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies V71 at the database boundary, independently of service locking. */
@SpringBootTest
class FiscalDocumentCommandUniquenessIntegrationTests extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CommandRepository commands;

    @Test
    void rejectsSecondDocumentForSameCommandButAllowsAnotherCommand() {
        UUID firstCommand = commands.findByNumber(28).orElseThrow().getId();
        UUID secondCommand = commands.findByNumber(29).orElseThrow().getId();
        List<UUID> inserted = new ArrayList<>();
        try {
            inserted.add(insert(firstCommand));
            assertThatThrownBy(() -> insert(firstCommand))
                    .isInstanceOf(DataIntegrityViolationException.class);
            inserted.add(insert(secondCommand));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM fiscal_document WHERE command_id = ?",
                    Integer.class, firstCommand)).isEqualTo(1);
        } finally {
            inserted.forEach(id -> jdbc.update("DELETE FROM fiscal_document WHERE id = ?", id));
        }
    }

    private UUID insert(UUID commandId) {
        return jdbc.queryForObject(
                "INSERT INTO fiscal_document (command_id) VALUES (?) RETURNING id",
                UUID.class, commandId);
    }
}
