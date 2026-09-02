package com.farelo.api.command;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the FARELO-031 seed: after Flyway applies
 * {@code V6__seed_commands_1_to_100.sql}, exactly 100 commands exist,
 * numbered 1-100, all {@code AVAILABLE}.
 *
 * <p>The exact-100 assertion relies on no other test leaving extra rows in
 * {@code command} behind — see {@code CommandRepositoryIntegrationTests},
 * which cleans up after itself for this reason.
 */
@SpringBootTest
class CommandSeedIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CommandRepository commandRepository;

    @Test
    void seedsExactlyOneHundredAvailableCommands() {
        assertThat(commandRepository.count()).isEqualTo(100);

        for (int sampleNumber : new int[] {1, 50, 100}) {
            Optional<Command> command = commandRepository.findByNumber(sampleNumber);

            assertThat(command).as("command #%d should exist", sampleNumber).isPresent();
            assertThat(command.get().getNumber()).isEqualTo(sampleNumber);
            assertThat(command.get().getStatus()).isEqualTo(CommandStatus.AVAILABLE);
        }
    }

}
