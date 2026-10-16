package com.farelo.api.command;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the FARELO-031 seed: after Flyway applies
 * {@code V6__seed_commands_1_to_100.sql}, each physical number 1-100
 * has its initial visit. Later visits and test-specific numbers may add rows.
 */
@SpringBootTest
class CommandSeedIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsInitialVisitsForAllOneHundredPhysicalNumbers() {
        Integer initialVisits = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM command
                WHERE number BETWEEN 1 AND 100 AND cycle_number = 1
                """, Integer.class);
        assertThat(initialVisits).isEqualTo(100);

        for (int sampleNumber : new int[] {1, 50, 100}) {
            Optional<Command> command = commandRepository.findByNumber(sampleNumber);

            assertThat(command).as("command #%d should exist", sampleNumber).isPresent();
            assertThat(command.get().getNumber()).isEqualTo(sampleNumber);
            assertThat(command.get().getStatus()).isEqualTo(CommandStatus.AVAILABLE);
        }
    }

}
