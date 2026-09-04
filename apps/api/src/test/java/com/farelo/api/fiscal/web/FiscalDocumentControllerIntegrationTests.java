package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.fiscal.FiscalDocument;
import com.farelo.api.fiscal.FiscalDocumentRepository;
import com.farelo.api.fiscal.FiscalDocumentStatus;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/commands/{number}/fiscal-documents}
 * (FARELO-156) and {@code POST
 * /api/v1/commands/{number}/fiscal-documents/{id}/transition} (FARELO-157),
 * against a real PostgreSQL instance (Testcontainers). Same format as
 * {@code PaymentControllerIntegrationTests}' own {@code listByCommand}/
 * {@code record} coverage.
 *
 * <p>Uses dedicated seeded command numbers 35-37 ({@link #listByCommand}, from
 * FARELO-156) and 85-89 ({@link #transition}, new in FARELO-157) — free
 * numbers per the registry maintained across this suite (see {@code
 * FiscalDocumentRepositoryIntegrationTests}' own 21, 23-25, {@code
 * FiscalDocumentServiceIntegrationTests}' own 26-27, and {@code
 * PaymentControllerIntegrationTests}' javadoc for the fuller registry).
 *
 * <p><b>{@link #listByCommand} stays unprotected</b> — see {@link
 * FiscalDocumentController}'s javadoc for why. No token/{@code
 * Authorization} header is sent by any {@code GET} test below, and none is
 * required.
 *
 * <p><b>{@link #transition} requires {@code ADMIN}/{@code MANAGER}</b> — see
 * {@link FiscalDocumentController}'s javadoc, "endpoint-exposure decision",
 * for the full reasoning. Every {@code POST .../transition} test below
 * mints a token via {@link #tokenFor}, same {@code tokenFor(UserRole)}
 * pattern {@code PaymentControllerIntegrationTests}/{@code
 * CommandControllerIntegrationTests} already established.
 *
 * <p>No {@code @BeforeEach}/{@code @AfterEach} cleanup needed: this class
 * never mutates a {@code Command}'s status, and every {@link FiscalDocument}
 * row it creates is scoped to its own dedicated command number, so leftover
 * rows from other test classes sharing the singleton Postgres container (see
 * {@link AbstractIntegrationTest}) never affect an assertion here — same
 * reasoning as {@code PaymentRepositoryIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FiscalDocumentControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_DOCUMENTS = 35;
    private static final int COMMAND_WITHOUT_DOCUMENTS = 36;
    private static final int OTHER_COMMAND_FOR_LEAK_CHECK = 37;

    // FARELO-157 (POST .../transition) — see class javadoc.
    private static final int TRANSITION_SUCCESS_NUMBER = 85;
    private static final int TRANSITION_NO_TOKEN_NUMBER = 86;
    private static final int TRANSITION_WRONG_ROLE_NUMBER = 87;
    private static final int TRANSITION_INVALID_MOVE_NUMBER = 88;
    private static final int TRANSITION_OWNER_COMMAND_NUMBER = 89;
    private static final int TRANSITION_OTHER_COMMAND_NUMBER = 90;

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private FiscalDocumentRepository fiscalDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    private FiscalDocument newDocument(int commandNumber) {
        return fiscalDocumentRepository.saveAndFlush(new FiscalDocument(command(commandNumber)));
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
    void returnsEmptyListWhenCommandHasNoFiscalDocuments() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/fiscal-documents", COMMAND_WITHOUT_DOCUMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/fiscal-documents", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listsFiscalDocumentsOldestFirstScopedToCommand() throws Exception {
        Command command = command(COMMAND_WITH_DOCUMENTS);
        Command otherCommand = command(OTHER_COMMAND_FOR_LEAK_CHECK);

        FiscalDocument first = fiscalDocumentRepository.save(new FiscalDocument(command));
        // Distinct, increasing createdAt for a deterministic order
        // assertion — same pattern PaymentControllerIntegrationTests uses.
        Thread.sleep(10);
        FiscalDocument second = fiscalDocumentRepository.save(new FiscalDocument(command));
        // A fiscal document on a different command must not leak into this
        // list.
        fiscalDocumentRepository.save(new FiscalDocument(otherCommand));

        mockMvc.perform(get("/api/v1/commands/{number}/fiscal-documents", COMMAND_WITH_DOCUMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[0].commandNumber").value(COMMAND_WITH_DOCUMENTS))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].documentNumber").value(nullValue()))
                .andExpect(jsonPath("$[0].accessKey").value(nullValue()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    // --- FARELO-157: POST .../fiscal-documents/{id}/transition ----------

    @Test
    void transitionMovesDocumentToNewStatusForAuthorizedRole() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_SUCCESS_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_SUCCESS_NUMBER,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(document.getId().toString()))
                .andExpect(jsonPath("$.commandNumber").value(TRANSITION_SUCCESS_NUMBER))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        assertThat(fiscalDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(FiscalDocumentStatus.PROCESSING);
    }

    @Test
    void transitionAcceptsManagerRoleToo() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_SUCCESS_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_SUCCESS_NUMBER,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTINGENCY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTINGENCY"));
    }

    @Test
    void transitionRejectsRequestWithNoAuthorizationHeader() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_NO_TOKEN_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_NO_TOKEN_NUMBER,
                        document.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(fiscalDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(FiscalDocumentStatus.PENDING);
    }

    @Test
    void transitionRejectsRoleOutsideAdminOrManager() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_WRONG_ROLE_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_WRONG_ROLE_NUMBER,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(fiscalDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(FiscalDocumentStatus.PENDING);
    }

    @Test
    void transitionRejectsIllegalMoveWithConflict() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_INVALID_MOVE_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_INVALID_MOVE_NUMBER,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AUTHORIZED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void transitionReturnsNotFoundForUnknownDocumentId() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_SUCCESS_NUMBER,
                        UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_NOT_FOUND"));
    }

    @Test
    void transitionReturnsNotFoundWhenDocumentBelongsToDifferentCommand() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_OWNER_COMMAND_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_OTHER_COMMAND_NUMBER,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_NOT_FOUND"));

        assertThat(fiscalDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(FiscalDocumentStatus.PENDING);
    }

    @Test
    void transitionReturnsNotFoundForUnknownCommandNumber() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_SUCCESS_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        999,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void transitionRejectsMissingStatusInBody() throws Exception {
        FiscalDocument document = newDocument(TRANSITION_SUCCESS_NUMBER);

        mockMvc.perform(post(
                        "/api/v1/commands/{number}/fiscal-documents/{id}/transition",
                        TRANSITION_SUCCESS_NUMBER,
                        document.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

}
