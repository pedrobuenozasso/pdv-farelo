package com.farelo.api.command.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

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
 *
 * <p><b>FARELO-124</b>: {@code open}/{@code close} now require a caller
 * role (see {@code CommandController}'s javadoc), so every {@code POST}
 * here mints a real token via {@link #tokenFor} and sends it as
 * {@code Authorization: Bearer <token>} — same {@code tokenFor(UserRole)}
 * pattern {@code ProductControllerIntegrationTests}/
 * {@code UserControllerIntegrationTests} established at FARELO-123.
 * {@code GET} is left with <b>no</b> header anywhere in this class — see
 * {@code findByNumber}'s own javadoc for why it stays unprotected (public
 * "Cardápio QR" dependency).
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
    private static final int RBAC_OPEN_NUMBER = 91;
    private static final int RBAC_CLOSE_NUMBER = 92;

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @AfterEach
    void resetMutatedTestCommands() {
        resetToAvailable(OPEN_TEST_NUMBER);
        resetToAvailable(CONFLICT_TEST_NUMBER);
        resetToAvailable(CLOSE_FROM_OPEN_NUMBER);
        resetToAvailable(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER);
        resetToAvailable(CLOSE_FROM_AVAILABLE_NUMBER);
        resetToAvailable(CLOSE_ALREADY_CLOSED_NUMBER);
        resetToAvailable(RBAC_OPEN_NUMBER);
        resetToAvailable(RBAC_CLOSE_NUMBER);
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

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
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
        mockMvc.perform(post("/api/v1/commands/{number}/open", OPEN_TEST_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(OPEN_TEST_NUMBER))
                .andExpect(jsonPath("$.status").value("OPEN"));

        Optional<Command> persisted = commandRepository.findByNumber(OPEN_TEST_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(CommandStatus.OPEN);
    }

    @Test
    void returnsConflictWhenCommandIsNotAvailable() throws Exception {
        String token = tokenFor(UserRole.ATTENDANT);

        // first open succeeds
        mockMvc.perform(post("/api/v1/commands/{number}/open", CONFLICT_TEST_NUMBER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // second open on the same (now OPEN) command is a business error
        mockMvc.perform(post("/api/v1/commands/{number}/open", CONFLICT_TEST_NUMBER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsCommandNotFoundWhenOpeningUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/open", 999)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void closesOpenCommand() throws Exception {
        setStatus(CLOSE_FROM_OPEN_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_OPEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
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

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_PAYMENT_REQUESTED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
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
        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_AVAILABLE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenClosingAlreadyClosedCommand() throws Exception {
        setStatus(CLOSE_ALREADY_CLOSED_NUMBER, CommandStatus.CLOSED);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_ALREADY_CLOSED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsCommandNotFoundWhenClosingUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/close", 999)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    // --- FARELO-124: RBAC on open()/close() -------------------------------

    @Test
    void rejectsOpenWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/open", RBAC_OPEN_NUMBER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // KITCHEN has no business opening/closing comandas — see
    // CommandController's javadoc.
    @Test
    void rejectsOpenWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/open", RBAC_OPEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsOpenAsAttendant() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/open", RBAC_OPEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsCloseWithNoAuthorizationHeader() throws Exception {
        setStatus(RBAC_CLOSE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", RBAC_CLOSE_NUMBER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ATTENDANT can open() but, deliberately, not close() — see
    // CommandController's javadoc for why closing is narrower.
    @Test
    void rejectsCloseWhenCallerRoleIsNotAllowed() throws Exception {
        setStatus(RBAC_CLOSE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", RBAC_CLOSE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsCloseAsCashier() throws Exception {
        setStatus(RBAC_CLOSE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", RBAC_CLOSE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk());
    }

}
