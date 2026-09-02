package com.farelo.api.command;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Command} maps correctly onto the table created by
 * {@code V5__create_command_table.sql}, against a real PostgreSQL instance.
 */
@SpringBootTest
class CommandRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CommandRepository commandRepository;

    @Test
    void savesAndFindsCommandByNumber() {
        Command command = commandRepository.saveAndFlush(new Command(42));

        assertThat(command.getId()).isNotNull();
        assertThat(command.getStatus()).isEqualTo(CommandStatus.AVAILABLE);
        assertThat(command.getCreatedAt()).isNotNull();
        assertThat(command.getUpdatedAt()).isNotNull();

        Optional<Command> found = commandRepository.findByNumber(42);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(command.getId());
        assertThat(found.get().getStatus()).isEqualTo(CommandStatus.AVAILABLE);
    }

}
