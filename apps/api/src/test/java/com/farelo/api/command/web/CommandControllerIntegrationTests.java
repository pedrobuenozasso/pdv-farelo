package com.farelo.api.command.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET}/{@code POST .../open}/{@code POST
 * .../close /api/v1/commands/{number}}, against a real PostgreSQL instance
 * (Testcontainers).
 *
 * <p>The mutating tests ({@code open}/{@code close}) use dedicated seeded
 * numbers (2-7) — distinct from the ones the {@code GET} tests read (1) and
 * {@code CommandSeedIntegrationTests} samples (1, 50, 100) — and reset
 * their status back to {@code AVAILABLE} in {@code @AfterEach}, since these
 * mutate state shared with every other test class via the singleton
 * Postgres container (see {@link AbstractIntegrationTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommandControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int OPEN_TEST_NUMBER = 2;
    private static final int CONFLICT_TEST_NUMBER = 3;
    private static final int CLOSE_FROM_OPEN_NUMBER = 4;
    private static final int CLOSE_FROM_PAYMENT_REQUESTED_NUMBER = 5;
    private static final int CLOSE_FROM_AVAILABLE_NUMBER = 6;
    private static final int CLOSE_ALREADY_CLOSED_NUMBER = 7;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @AfterEach
    void resetMutatedTestCommands() {
        resetToAvailable(OPEN_TEST_NUMBER);
        resetToAvailable(CONFLICT_TEST_NUMBER);
        resetToAvailable(CLOSE_FROM_OPEN_NUMBER);
        resetToAvailable(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER);
        resetToAvailable(CLOSE_FROM_AVAILABLE_NUMBER);
        resetToAvailable(CLOSE_ALREADY_CLOSED_NUMBER);
    }

    private void resetToAvailable(int number) {
        commandRepository.findByNumber(number).ifPresent(command -> {
            command.setStatus(CommandStatus.AVAILABLE);
            commandRepository.save(command);
        });
    }

    // Test-only setup: sets a command straight to a given status, bypassing
    // the API (there's no endpoint yet to reach PAYMENT_REQUESTED, for
    // instance) — just to arrange the scenario before exercising close().
    private void setStatus(int number, CommandStatus status) {
        Command command = commandRepository.findByNumber(number).orElseThrow();
        command.setStatus(status);
        commandRepository.save(command);
    }

    @Test
    void returnsCommandForExistingNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returnsCommandNotFoundForUnknownNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void opensAvailableCommand() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/open", OPEN_TEST_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(OPEN_TEST_NUMBER))
                .andExpect(jsonPath("$.status").value("OPEN"));

        Optional<Command> persisted = commandRepository.findByNumber(OPEN_TEST_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(CommandStatus.OPEN);
    }

    @Test
    void returnsConflictWhenCommandIsNotAvailable() throws Exception {
        // first open succeeds
        mockMvc.perform(post("/api/v1/commands/{number}/open", CONFLICT_TEST_NUMBER))
                .andExpect(status().isOk());

        // second open on the same (now OPEN) command is a business error
        mockMvc.perform(post("/api/v1/commands/{number}/open", CONFLICT_TEST_NUMBER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsCommandNotFoundWhenOpeningUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/open", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void closesOpenCommand() throws Exception {
        setStatus(CLOSE_FROM_OPEN_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_OPEN_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(CLOSE_FROM_OPEN_NUMBER))
                .andExpect(jsonPath("$.status").value("CLOSED"));

        Optional<Command> persisted = commandRepository.findByNumber(CLOSE_FROM_OPEN_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(CommandStatus.CLOSED);
    }

    @Test
    void closesPaymentRequestedCommand() throws Exception {
        setStatus(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER, CommandStatus.PAYMENT_REQUESTED);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_PAYMENT_REQUESTED_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        Optional<Command> persisted = commandRepository.findByNumber(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(CommandStatus.CLOSED);
    }

    @Test
    void returnsConflictWhenClosingAvailableCommand() throws Exception {
        // CLOSE_FROM_AVAILABLE_NUMBER is left at its seed default
        // (AVAILABLE) — never opened, so it can't be closed.
        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_AVAILABLE_NUMBER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenClosingAlreadyClosedCommand() throws Exception {
        setStatus(CLOSE_ALREADY_CLOSED_NUMBER, CommandStatus.CLOSED);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_ALREADY_CLOSED_NUMBER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsCommandNotFoundWhenClosingUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/close", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

}
