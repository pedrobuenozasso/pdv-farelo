package com.farelo.api.command;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Command} maps correctly onto the table created by
 * {@code V5__create_command_table.sql}, against a real PostgreSQL instance.
 *
 * <p>Uses a number outside the 1-100 range seeded by
 * {@code V6__seed_commands_1_to_100.sql} (FARELO-031) — those numbers are
 * already taken by the seed (unique constraint) — and deletes the command
 * it creates in {@code @AfterEach}, so it doesn't leave a stray row behind
 * that would throw off {@code CommandSeedIntegrationTests}' exact count of
 * 100 (same shared-container reasoning as {@code AbstractIntegrationTest}).
 */
@SpringBootTest
class CommandRepositoryIntegrationTests extends AbstractIntegrationTest {

    private static final int TEST_NUMBER = 101;

    @Autowired
    private CommandRepository commandRepository;

    @AfterEach
    void deleteTestCommand() {
        commandRepository.findByNumber(TEST_NUMBER).ifPresent(commandRepository::delete);
    }

    @Test
    void savesAndFindsCommandByNumber() {
        Command command = commandRepository.saveAndFlush(new Command(TEST_NUMBER));

        assertThat(command.getId()).isNotNull();
        assertThat(command.getStatus()).isEqualTo(CommandStatus.AVAILABLE);
        assertThat(command.getCreatedAt()).isNotNull();
        assertThat(command.getUpdatedAt()).isNotNull();

        Optional<Command> found = commandRepository.findByNumber(TEST_NUMBER);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(command.getId());
        assertThat(found.get().getStatus()).isEqualTo(CommandStatus.AVAILABLE);
    }

}
