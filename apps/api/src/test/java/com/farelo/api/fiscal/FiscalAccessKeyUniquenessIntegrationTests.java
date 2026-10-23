package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises V65's identity guard against PostgreSQL, including nullable drafts. */
@SpringBootTest
class FiscalAccessKeyUniquenessIntegrationTests extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CommandRepository commands;

    @Test
    void duplicateAccessKeyIsRejectedAcrossCommandsButNullDraftsAreAllowed() {
        UUID firstCommand = commands.findByNumber(21).orElseThrow().getId();
        UUID secondCommand = commands.findByNumber(22).orElseThrow().getId();
        String key = String.format("%044d", new BigInteger(
                UUID.randomUUID().toString().replace("-", ""), 16));

        List<UUID> inserted = new ArrayList<>();
        try {
            inserted.add(insert(firstCommand, key));
            assertThatThrownBy(() -> insert(secondCommand, key))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM fiscal_document WHERE access_key = ?", Integer.class, key))
                    .isEqualTo(1);

            inserted.add(insert(firstCommand, null));
            inserted.add(insert(secondCommand, null));
        } finally {
            inserted.forEach(id -> jdbc.update("DELETE FROM fiscal_document WHERE id = ?", id));
        }
    }

    private UUID insert(UUID commandId, String key) {
        return jdbc.queryForObject(
                "INSERT INTO fiscal_document (command_id, access_key) VALUES (?, ?) RETURNING id",
                UUID.class, commandId, key);
    }
}
