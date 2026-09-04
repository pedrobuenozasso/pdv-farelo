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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET}/{@code POST .../open
 * /api/v1/commands/{number}}, against a real PostgreSQL instance
 * (Testcontainers).
 *
 * <p><b>FARELO-143 moved every {@code POST .../close} test out of this
 * class</b>, into {@code PaymentControllerIntegrationTests} — see {@code
 * CommandController}'s updated javadoc for why the route itself moved to
 * {@code PaymentController#close}. Numbers 4-7 and 92 (previously used by
 * those tests) are retired from this file, not reused here — see that
 * class's javadoc for their new home and the new FARELO-143 numbers added
 * alongside them.
 *
 * <p>The mutating tests still here ({@code open}) use dedicated seeded
 * numbers (2-3) — distinct from the ones the {@code GET} tests read (1) and
 * {@code CommandSeedIntegrationTests} samples (1, 50, 100) — and reset
 * their status back to {@code AVAILABLE} in {@code @AfterEach}, since these
 * mutate state shared with every other test class via the singleton
 * Postgres container (see {@link AbstractIntegrationTest}).
 *
 * <p><b>FARELO-124</b>: {@code open} requires a caller role (see {@code
 * CommandController}'s javadoc), so every {@code POST} here mints a real
 * token via {@link #tokenFor} and sends it as
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
    private static final int RBAC_OPEN_NUMBER = 91;

    // FARELO-190/191 — 4/5/92 are free (retired from this file's own
    // FARELO-143 tests, "not reused here" per this class's javadoc).
    private static final int CUSTOMER_UPDATE_NUMBER = 4;
    private static final int RBAC_CUSTOMER_NUMBER = 5;

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
        resetToAvailable(RBAC_OPEN_NUMBER);
        clearCustomerInfo(CUSTOMER_UPDATE_NUMBER);
        clearCustomerInfo(RBAC_CUSTOMER_NUMBER);
    }

    private void resetToAvailable(int number) {
        commandRepository.findByNumber(number).ifPresent(command -> {
            command.setStatus(CommandStatus.AVAILABLE);
            commandRepository.save(command);
        });
    }

    private void clearCustomerInfo(int number) {
        commandRepository.findByNumber(number).ifPresent(command -> {
            command.setCustomerName(null);
            command.setCustomerPhone(null);
            commandRepository.save(command);
        });
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

    // --- FARELO-124: RBAC on open() ----------------------------------------

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

    // --- FARELO-190/191: PATCH .../customer ---------------------------------

    @Test
    void updatesCustomerNameAndNormalizesPhone() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", CUSTOMER_UPDATE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerName": "Maria Souza", "customerPhone": "(31) 99876-5432" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Maria Souza"))
                .andExpect(jsonPath("$.customerPhone").value("5531998765432"));

        Optional<Command> persisted = commandRepository.findByNumber(CUSTOMER_UPDATE_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCustomerName()).isEqualTo("Maria Souza");
        assertThat(persisted.get().getCustomerPhone()).isEqualTo("5531998765432");
    }

    @Test
    void doesNotPrependCountryCodeWhenAlreadyPresent() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", CUSTOMER_UPDATE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerPhone": "+55 31 99876-5432" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPhone").value("5531998765432"));
    }

    @Test
    void clearsCustomerInfoWhenFieldsOmitted() throws Exception {
        String token = tokenFor(UserRole.ATTENDANT);

        mockMvc.perform(patch("/api/v1/commands/{number}/customer", CUSTOMER_UPDATE_NUMBER)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerName": "Ana", "customerPhone": "31988887777" }
                                """))
                .andExpect(status().isOk());

        // omitting both fields (full-replace) clears the previously-set values
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", CUSTOMER_UPDATE_NUMBER)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.customerPhone").doesNotExist());

        Optional<Command> persisted = commandRepository.findByNumber(CUSTOMER_UPDATE_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCustomerName()).isNull();
        assertThat(persisted.get().getCustomerPhone()).isNull();
    }

    @Test
    void rejectsCustomerPhoneWithLetters() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", CUSTOMER_UPDATE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerPhone": "not-a-phone" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsCommandNotFoundWhenUpdatingCustomerForUnknownNumber() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", 999)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void rejectsCustomerUpdateWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", RBAC_CUSTOMER_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // KITCHEN has no business editing a comanda's customer info either —
    // same reasoning as rejectsOpenWhenCallerRoleIsNotAllowed above.
    @Test
    void rejectsCustomerUpdateWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/customer", RBAC_CUSTOMER_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

}
