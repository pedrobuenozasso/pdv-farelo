package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.fiscal.FiscalDocument;
import com.farelo.api.fiscal.FiscalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/commands/{number}/fiscal-documents}
 * (FARELO-156), against a real PostgreSQL instance (Testcontainers). Same
 * format as {@code PaymentControllerIntegrationTests}' own {@code
 * listByCommand} coverage — the only endpoint this ticket adds.
 *
 * <p>Uses dedicated seeded command numbers 35-37 — free numbers per the
 * registry maintained across this suite (see {@code
 * FiscalDocumentRepositoryIntegrationTests}' own 21-23, and {@code
 * PaymentControllerIntegrationTests}' javadoc for the fuller registry).
 *
 * <p><b>Stays unprotected</b> — see {@link FiscalDocumentController}'s
 * javadoc for why. No token/{@code Authorization} header is sent by any test
 * below, and none is required.
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private FiscalDocumentRepository fiscalDocumentRepository;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
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

}
